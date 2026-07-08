// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static java.time.ZoneOffset.UTC;
import static org.apache.logging.log4j.util.Strings.EMPTY;
import static org.hiero.mirror.web3.convert.BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_STORAGE_DISCOVERY_CANDIDATES;
import static org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState.STATE_ID;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.inject.Named;
import java.time.Instant;
import java.time.YearMonth;
import lombok.CustomLog;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.state.Utils;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.utils.Suppliers;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.springframework.beans.factory.annotation.Qualifier;

@Named
@CustomLog
public abstract class ContractCallService {

    static final String EVM_INVOCATION_METRIC = "hiero.mirror.web3.evm.invocation";
    static final String GAS_LIMIT_METRIC = "hiero.mirror.web3.evm.gas.limit";
    static final String GAS_USED_METRIC = "hiero.mirror.web3.evm.gas.used";
    static final String TAG_BLOCK = "block";
    static final String TAG_ITERATION = "iteration";
    static final String TAG_STATUS = "status";
    static final String TAG_TYPE = "type";

    protected final EvmProperties evmProperties;

    private final MeterProvider<Counter> invocationCounter;
    private final MeterProvider<Counter> gasLimitCounter;
    private final MeterProvider<Counter> gasUsedCounter;
    private final RecordFileService recordFileService;
    private final ThrottleProperties throttleProperties;
    private final ThrottleManager throttleManager;
    private final TransactionExecutionService transactionExecutionService;
    private final CacheProperties cacheProperties;

    /**
     * Application-wide cache of hashes of {@code (callData, receiver)} combinations whose requests accessed enough
     * contract storage slots to justify running the storage discovery pass on subsequent identical requests.
     */
    private final Cache<Long, Boolean> storageDiscoveryCandidates;

    @SuppressWarnings("java:S107")
    protected ContractCallService(
            ThrottleManager throttleManager,
            ThrottleProperties throttleProperties,
            MeterRegistry meterRegistry,
            RecordFileService recordFileService,
            EvmProperties evmProperties,
            TransactionExecutionService transactionExecutionService,
            CacheProperties cacheProperties,
            @Qualifier(CACHE_STORAGE_DISCOVERY_CANDIDATES) Cache<Long, Boolean> storageDiscoveryCandidates) {
        this.invocationCounter = Counter.builder(EVM_INVOCATION_METRIC)
                .description("The number of EVM invocations")
                .withRegistry(meterRegistry);
        this.gasLimitCounter = Counter.builder(GAS_LIMIT_METRIC)
                .description("The amount of gas limit sent in the request")
                .withRegistry(meterRegistry);
        this.gasUsedCounter = Counter.builder(GAS_USED_METRIC)
                .description("The amount of gas consumed by the EVM")
                .withRegistry(meterRegistry);
        this.recordFileService = recordFileService;
        this.throttleProperties = throttleProperties;
        this.throttleManager = throttleManager;
        this.evmProperties = evmProperties;
        this.transactionExecutionService = transactionExecutionService;
        this.cacheProperties = cacheProperties;
        this.storageDiscoveryCandidates = storageDiscoveryCandidates;
    }

    @VisibleForTesting
    public EvmTransactionResult callContract(CallServiceParameters params) throws MirrorEvmTransactionException {
        return ContractCallContext.run(context -> callContract(params, context));
    }

    /**
     * This method is responsible for calling a smart contract function. The method is divided into two main parts:
     * <p>
     * 1. If the call is historical, the method retrieves the corresponding record file and initializes the contract
     * call context with the historical state. The method then proceeds to call the contract.
     * </p>
     * <p>
     * 2. If the call is not historical, the method initializes the contract call context with the current state and
     * proceeds to call the contract.
     * </p>
     *
     * @param params the call service parameters
     * @param ctx    the contract call context
     * @return {@link EvmTransactionResult} of the contract call
     * @throws MirrorEvmTransactionException if any pre-checks fail with {@link IllegalStateException} or
     *                                       {@link IllegalArgumentException}
     */
    protected final EvmTransactionResult callContract(CallServiceParameters params, ContractCallContext ctx)
            throws MirrorEvmTransactionException {
        // Opcode tracing (debug) relies on a single EVM execution, so the storage discovery pass, which executes the
        // call an additional time, must be skipped for it.
        final var discoveryEligible =
                cacheProperties.isEnableBatchContractSlotCaching() && ctx.getOpcodeContext() == null;

        // The discovery pass is only run for requests previously seen to be storage-heavy (their (callData, receiver)
        // hash is a known candidate), avoiding the extra EVM execution for the common, light-weight case.
        if (discoveryEligible && isStorageDiscoveryCandidate(params)) {
            runStorageDiscovery(params, ctx);
            ctx.finishStorageDiscovery(STATE_ID);
            // Record the timestamp mode used during discovery so the execution pass can safely consume the
            // discovered slot keys only for matching batch queries (historical vs. latest).
            ctx.setDiscoveryBlockTimestamp(ctx.getTimestamp().orElse(null));
        }

        return executeCallInContext(params, ctx);
    }

    /**
     * Returns whether a request identified by the hash of its {@code (callData, receiver)} has previously been flagged
     * as accessing enough contract storage slots to benefit from the storage discovery pass.
     */
    private boolean isStorageDiscoveryCandidate(final CallServiceParameters params) {
        return storageDiscoveryCandidates.getIfPresent(hashCallDataAndReceiver(params)) != null;
    }

    /**
     * Flags the request's {@code (callData, receiver)} hash as a storage discovery candidate when the execution pass
     * resolved more contract storage slots than the configured threshold, so that subsequent identical requests run
     * the discovery pass and benefit from batch slot preloading.
     */
    private void recordStorageDiscoveryCandidate(final CallServiceParameters params, final ContractCallContext ctx) {
        if (ctx.getContractStorageReadCount() > cacheProperties.getContractStorageDiscoveryThreshold()) {
            storageDiscoveryCandidates.put(hashCallDataAndReceiver(params), Boolean.TRUE);
        }
    }

    /**
     * Computes a light-weight (murmur3) hash of the request's {@code callData} and {@code receiver} fields, used as the
     * key into the shared storage discovery candidate cache.
     */
    private static long hashCallDataAndReceiver(final CallServiceParameters params) {
        final var hasher = Hashing.murmur3_128().newHasher();
        final var callData = params.getCallData();
        if (callData != null) {
            hasher.putBytes(callData);
        }
        final var receiver = params.getReceiver();
        if (receiver != null) {
            hasher.putBytes(receiver.toArrayUnsafe());
        }
        return hasher.hash().asLong();
    }

    private EvmTransactionResult executeCallInContext(CallServiceParameters params, ContractCallContext ctx)
            throws MirrorEvmTransactionException {
        prepareCallContext(params, ctx);
        return doProcessCall(params, params.getGas(), false, ctx);
    }

    private void prepareCallContext(final CallServiceParameters params, final ContractCallContext ctx) {
        ctx.setCallServiceParameters(params);
        ctx.setBlockSupplier(Suppliers.memoize(() ->
                recordFileService.findByBlockType(params.getBlock()).orElseThrow(BlockNumberNotFoundException::new)));
    }

    /**
     * Runs a fast discovery pass that records contract storage slot accesses in the {@link ContractCallContext} read
     * cache without querying the database.
     */
    private void runStorageDiscovery(final CallServiceParameters params, final ContractCallContext ctx) {
        ctx.setStorageDiscoveryMode(true);
        try {
            prepareCallContext(params, ctx);
            doProcessCall(params, params.getGas(), true, ctx);
        } catch (MirrorEvmTransactionException e) {
            log.debug("Storage discovery pass ended with status {}", e.getMessage());
        } finally {
            ctx.setStorageDiscoveryMode(false);
        }
    }

    protected final EvmTransactionResult doProcessCall(
            CallServiceParameters params, long estimatedGas, boolean estimate, final ContractCallContext ctx)
            throws MirrorEvmTransactionException {
        EvmTransactionResult result = null;
        var status = ResponseCodeEnum.SUCCESS.toString();

        try {
            result = transactionExecutionService.execute(params, estimatedGas);

            if (!estimate) {
                validateResult(result, params);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new MirrorEvmTransactionException(e.getMessage(), EMPTY);
        } catch (MirrorEvmTransactionException e) {
            // This result is needed in case of exception to be still able to call restoreGasToBucket method
            result = e.getResult();
            status = e.getMessage();
            throw e;
        } finally {
            if (!estimate) {
                restoreGasToBucket(result, params.getGas());

                // Only record metric if EVM is invoked and not inside estimate loop
                if (result != null) {
                    updateMetrics(params, result.gasUsed(), 1, status);
                }
            }

            final var discoveryEligible =
                    cacheProperties.isEnableBatchContractSlotCaching() && ctx.getOpcodeContext() == null;
            if (discoveryEligible) {
                recordStorageDiscoveryCandidate(params, ctx);
            }
        }
        return result;
    }

    private void restoreGasToBucket(EvmTransactionResult result, long gasLimit) {
        // If the transaction fails, gasUsed is equal to gasLimit, so restore the configured refund percent
        // of the gasLimit value back in the bucket.
        final var gasLimitToRestoreBaseline = (long) (gasLimit * throttleProperties.getGasLimitRefundPercent() / 100f);
        if (result == null || (!result.isSuccessful() && gasLimit == result.gasUsed())) {
            throttleManager.restore(gasLimitToRestoreBaseline);
        } else {
            // The transaction was successful or reverted, so restore the remaining gas back in the bucket or
            // the configured refund percent of the gasLimit value back in the bucket - whichever is lower.
            final var gasRemaining = gasLimit - result.gasUsed();
            throttleManager.restore(Math.min(gasRemaining, gasLimitToRestoreBaseline));
        }
    }

    protected void validateResult(final EvmTransactionResult txnResult, final CallServiceParameters params) {
        if (!txnResult.isSuccessful()) {
            var revertReasonHex = txnResult.getErrorMessage().orElse(HEX_PREFIX);
            var detail = maybeDecodeSolidityErrorStringToReadableMessage(revertReasonHex);
            throw new MirrorEvmTransactionException(
                    txnResult.responseCodeEnum().protoName(), detail, revertReasonHex, txnResult);
        }
    }

    protected final void updateMetrics(CallServiceParameters parameters, long gasUsed, int iterations, String status) {
        final var block = getBlock();
        final var callType = parameters.getCallType().toString();
        final var iterationTag = String.valueOf(iterations);
        var tags = Tags.of(TAG_STATUS, status, TAG_TYPE, callType);
        invocationCounter.withTags(tags.and(TAG_BLOCK, block)).increment();
        gasUsedCounter.withTags(tags.and(TAG_ITERATION, iterationTag)).increment(gasUsed);
    }

    protected final void updateGasLimitMetric(final CallServiceParameters parameters) {
        final var callType = parameters.getCallType().toString();
        gasLimitCounter.withTag(TAG_TYPE, callType).increment(parameters.getGas());
    }

    private String getBlock() {
        return ContractCallContext.get()
                .getTimestamp()
                .filter(t -> t <= Utils.getCurrentTimestamp()) // Filter future timestamps to reduce cardinality
                .map(t ->
                        YearMonth.from(Instant.ofEpochSecond(0L, t).atZone(UTC)).toString())
                .orElse(BlockType.LATEST.toString());
    }
}
