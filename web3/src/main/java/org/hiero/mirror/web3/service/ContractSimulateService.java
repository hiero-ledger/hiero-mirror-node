// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.BYTECODE_STATE_ID;
import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.STORAGE_STATE_ID;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.SIMULATE;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_SIMULATE;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.evm.utils.EvmTokenUtils;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.state.Utils;
import org.hiero.mirror.web3.state.keyvalue.AccountReadableKVState;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.SimulateCall;
import org.hiero.mirror.web3.viewmodel.SimulateCallResult;
import org.hiero.mirror.web3.viewmodel.SimulateLog;
import org.hiero.mirror.web3.viewmodel.SimulateRequest;
import org.hiero.mirror.web3.viewmodel.SimulateResponse;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.QueryTimeoutException;

@Named
public class ContractSimulateService extends ContractCallService {

    private static final String REVERT_STATUS = "0x0";
    private static final String SUCCESS_STATUS = "0x1";

    private static final Address TRANSFER_EVENT_EMITTER =
            Address.fromHexString("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

    private static final String TRANSFER_EVENT_TOPIC0 = Hash.keccak256(org.apache.tuweni.bytes.Bytes.wrap(
                    "Transfer(address,address,uint256)".getBytes(StandardCharsets.UTF_8)))
            .toHexString();

    private final RecordFileService recordFileService;
    private final Web3Properties web3Properties;

    public ContractSimulateService(
            EvmProperties evmProperties,
            MeterRegistry meterRegistry,
            RecordFileService recordFileService,
            ThrottleManager throttleManager,
            ThrottleProperties throttleProperties,
            TransactionExecutionService transactionExecutionService,
            Web3Properties web3Properties) {
        super(
                throttleManager,
                throttleProperties,
                meterRegistry,
                recordFileService,
                evmProperties,
                transactionExecutionService);
        this.recordFileService = recordFileService;
        this.web3Properties = web3Properties;
    }

    public SimulateResponse simulate(final SimulateRequest request) {
        final var remainingGas = new AtomicLong(request.totalGas());
        try {
            return new SimulateResponse(runSimulation(request, remainingGas));
        } catch (RuntimeException e) {
            throttleManager.restore(remainingGas.get());
            throw e;
        }
    }

    // The address's long-zero number and the numbers of committed accounts aliased by it.
    private Set<Long> accountNums(final Map<Object, Object> accounts, final Bytes address) {
        final var accountNums = new HashSet<Long>();
        final var longZeroId = DomainUtils.fromEvmAddress(address.toByteArray());
        if (longZeroId != null) {
            accountNums.add(longZeroId.getNum());
        }
        for (final var cached : accounts.values()) {
            if (cached instanceof Account account && address.equals(account.alias()) && account.hasAccountId()) {
                accountNums.add(account.accountIdOrThrow().accountNumOrElse(0L));
            }
        }
        return accountNums;
    }

    private String addressTopic(final Address address) {
        return org.apache.tuweni.bytes.Bytes32.leftPad(address.getBytes()).toHexString();
    }

    // Overrides also replace the state committed by earlier entries, which is read before them.
    private void applyStateOverrides(final ContractCallContext context, final List<StateOverride> overrides) {
        final var overridesByAddress = Utils.toOverrideMap(overrides);
        context.getStateOverrides().putAll(overridesByAddress);

        final var accounts = context.getCommittedCacheState(AccountReadableKVState.STATE_ID);
        for (final var entry : overridesByAddress.entrySet()) {
            final var address = entry.getKey();
            final var stateOverride = entry.getValue();
            final var accountNums = accountNums(accounts, address);

            // Other committed account changes are kept.
            for (final var committed : accounts.entrySet()) {
                if (committed.getValue() instanceof Account account && isOverridden(account, address, accountNums)) {
                    committed.setValue(AccountReadableKVState.withStateOverride(account, stateOverride));
                }
            }
            context.getReadCacheState(AccountReadableKVState.STATE_ID)
                    .values()
                    .removeIf(
                            cached -> cached instanceof Account account && isOverridden(account, address, accountNums));

            // Dropped code and slots are read again through the override.
            if (stateOverride.getCode() != null) {
                for (final var cache : List.of(
                        context.getCommittedCacheState(BYTECODE_STATE_ID),
                        context.getReadCacheState(BYTECODE_STATE_ID))) {
                    cache.keySet()
                            .removeIf(cachedKey -> cachedKey instanceof ContractID contractId
                                    && isOverridden(contractId, address, accountNums));
                }
            }
            for (final var cache : List.of(
                    context.getCommittedCacheState(STORAGE_STATE_ID), context.getReadCacheState(STORAGE_STATE_ID))) {
                cache.keySet()
                        .removeIf(cachedKey -> cachedKey instanceof SlotKey slotKey
                                && isOverridden(slotKey.contractID(), address, accountNums)
                                && isOverriddenSlot(stateOverride, slotKey));
            }
        }
    }

    private Address contractAddress(final ContractID contractID) {
        if (contractID == null) {
            return Address.ZERO;
        }
        if (contractID.hasEvmAddress()) {
            return Address.wrap(org.apache.tuweni.bytes.Bytes.wrap(
                    contractID.evmAddressOrThrow().toByteArray()));
        }
        final var entityId =
                EntityId.of(contractID.shardNum(), contractID.realmNum(), contractID.contractNumOrElse(0L));
        return EvmTokenUtils.toAddress(entityId);
    }

    private SimulateCallResult executeCall(
            final ContractExecutionParameters params,
            final ContractCallContext context,
            final SimulatedBlock block,
            final long logIndex,
            final long transactionIndex,
            final long requestCallIndex) {
        final var result = callContract(params, context);
        final var transactionHash = syntheticTransactionHash(result, requestCallIndex);
        final var contractLogs = mapLogs(result, block, logIndex, transactionIndex, transactionHash);
        final var transferLogs =
                mapTransferLogs(context, block, logIndex + contractLogs.size(), transactionIndex, transactionHash);
        final var logs = new ArrayList<SimulateLog>(contractLogs.size() + transferLogs.size());
        logs.addAll(contractLogs);
        logs.addAll(transferLogs);
        return new SimulateCallResult(Utils.toHex(result.gasUsed()), logs, result.contractCallResult(), SUCCESS_STATUS);
    }

    private void failIfTimedOut(final ContractCallContext context) {
        final long elapsed = System.currentTimeMillis() - context.getStartTime();
        if (elapsed >= web3Properties.getRequestTimeout(SIMULATE).toMillis()) {
            throw new QueryTimeoutException("Transaction timed out after %s ms".formatted(elapsed));
        }
    }

    private boolean isOverridden(final Account account, final Bytes address, final Set<Long> accountNums) {
        return address.equals(account.alias())
                || (account.hasAccountId()
                        && accountNums.contains(account.accountIdOrThrow().accountNumOrElse(0L)));
    }

    private boolean isOverridden(
            @Nullable final ContractID contractId, final Bytes address, final Set<Long> accountNums) {
        if (contractId == null) {
            return false;
        }
        return contractId.hasEvmAddress()
                ? address.equals(contractId.evmAddressOrThrow())
                : accountNums.contains(contractId.contractNumOrElse(0L));
    }

    private boolean isOverriddenSlot(final StateOverride stateOverride, final SlotKey slotKey) {
        if (!stateOverride.getState().isEmpty()) {
            return true;
        }
        for (final var storageEntry : stateOverride.getStateDiff()) {
            if (Utils.hexEqualsBytes(storageEntry.getKey(), slotKey.key())) {
                return true;
            }
        }
        return false;
    }

    private List<SimulateLog> mapLogs(
            final EvmTransactionResult result,
            final SimulatedBlock block,
            final long startingLogIndex,
            final long transactionIndex,
            final String transactionHash) {
        final var functionResult = result.functionResult();
        if (functionResult == null || functionResult.logInfo().isEmpty()) {
            return List.of();
        }

        final var logs = new ArrayList<SimulateLog>(functionResult.logInfo().size());
        var index = startingLogIndex;
        for (final var logInfo : functionResult.logInfo()) {
            logs.add(new SimulateLog(
                    contractAddress(logInfo.contractID()).toHexString(),
                    block.hash(),
                    block.number(),
                    Utils.withHexPrefix(logInfo.data().toHex()),
                    index,
                    false,
                    topics(logInfo.topic()),
                    transactionHash,
                    transactionIndex));
            index++;
        }
        return logs;
    }

    private List<SimulateLog> mapTransferLogs(
            final ContractCallContext context,
            final SimulatedBlock block,
            final long startingLogIndex,
            final long transactionIndex,
            final String transactionHash) {
        final var transfers = context.getCapturedTransfers();
        if (transfers.isEmpty()) {
            return List.of();
        }

        final var logs = new ArrayList<SimulateLog>(transfers.size());
        var index = startingLogIndex;
        for (final var transfer : transfers) {
            logs.add(new SimulateLog(
                    TRANSFER_EVENT_EMITTER.toHexString(),
                    block.hash(),
                    block.number(),
                    transfer.value().toHexString(),
                    index,
                    false,
                    List.of(TRANSFER_EVENT_TOPIC0, addressTopic(transfer.from()), addressTopic(transfer.to())),
                    transactionHash,
                    transactionIndex));
            index++;
        }
        return logs;
    }

    private List<SimulateCallResult> runSimulation(final SimulateRequest request, final AtomicLong remainingGas) {
        if (evmProperties.isSharedWritableState()) {
            // Otherwise writes flush into a cross-request cache shared by other users' calls.
            throw new IllegalStateException(
                    "hiero.mirror.web3.evm.sharedWritableState must be disabled to use /contracts/simulate.");
        }

        // Resolved once so every entry builds on the same anchor block.
        final var anchor =
                recordFileService.findByBlockType(request.getBlock()).orElseThrow(BlockNumberNotFoundException::new);

        return ContractCallContext.run(context -> {
            context.setApi(SIMULATE);
            context.setTraceTransfers(request.isTraceTransfers());
            context.setStateOverrides(new HashMap<>());
            final var results = new ArrayList<SimulateCallResult>();
            // Seeds the synthetic transaction hash; transaction_index resets per entry and would collide.
            long requestCallIndex = 0;
            long entryIndex = 0;
            long blockNumber = anchor.getIndex();

            for (final var blockCall : request.getBlockStateCalls()) {
                if (entryIndex > 0) {
                    context.commitWriteCache();
                }
                context.setBlockOverrideNumber(blockNumber);
                context.setBlockOverrideTimeNanos(null);
                context.applyBlockOverride(blockCall.getBlockOverride());
                blockNumber = context.getBlockOverrideNumber();
                applyStateOverrides(context, blockCall.getStateOverrides());
                // Rebuilds the block singletons for this entry's header.
                for (final var blockStateId : List.of(BLOCKS_STATE_ID, BLOCK_STREAM_INFO_STATE_ID)) {
                    context.getReadCacheState(blockStateId).clear();
                    context.getCommittedCacheState(blockStateId).clear();
                }

                final var block = new SimulatedBlock(anchor, entryIndex, blockNumber);
                long logIndex = 0;
                long transactionIndex = 0;
                for (final var call : blockCall.getCalls()) {
                    // A timeout inside the executor would otherwise surface as a failed call.
                    failIfTimedOut(context);
                    final var params = toExecutionParameters(request.getBlock(), call);
                    context.getCapturedTransfers().clear();
                    context.getTransferFrameStarts().clear();
                    remainingGas.addAndGet(-call.getGas());

                    try {
                        final var callResult =
                                executeCall(params, context, block, logIndex, transactionIndex, requestCallIndex);
                        results.add(callResult);
                        logIndex += callResult.logs().size();
                    } catch (MirrorEvmTransactionException e) {
                        final var partialResult = e.getResult();
                        results.add(new SimulateCallResult(
                                Utils.toHex(partialResult != null ? partialResult.gasUsed() : 0L),
                                List.of(),
                                StringUtils.defaultIfEmpty(e.getData(), HEX_PREFIX),
                                REVERT_STATUS));
                    }

                    transactionIndex++;
                    requestCallIndex++;
                }

                entryIndex++;
                blockNumber++;
            }

            return results;
        });
    }

    private String syntheticTransactionHash(final EvmTransactionResult result, final long requestCallIndex) {
        final var seed = org.apache.tuweni.bytes.Bytes.concatenate(
                org.apache.tuweni.bytes.Bytes.fromHexString(result.contractCallResult()),
                org.apache.tuweni.bytes.Bytes.ofUnsignedLong(requestCallIndex));
        return Hash.keccak256(seed).toHexString();
    }

    private ContractExecutionParameters toExecutionParameters(final BlockType block, final SimulateCall call) {
        final var sender = call.getFrom() != null ? Address.fromHexString(call.getFrom()) : Address.ZERO;
        final var receiver = StringUtils.isNotEmpty(call.getTo()) ? Address.fromHexString(call.getTo()) : Address.ZERO;
        final var data = call.getData() != null ? call.getData() : HEX_PREFIX;

        return ContractExecutionParameters.builder()
                .block(block)
                .callData(hexToBytes(data))
                .callType(ETH_SIMULATE)
                .gas(call.getGas())
                .gasPrice(call.getGasPrice())
                .isEstimate(false)
                .isStatic(false)
                .receiver(receiver)
                .sender(sender)
                .value(call.getValue())
                .build();
    }

    private List<String> topics(final List<com.hedera.pbj.runtime.io.buffer.Bytes> topics) {
        final var hexTopics = new ArrayList<String>(topics.size());
        for (final var topic : topics) {
            hexTopics.add(Utils.withHexPrefix(topic.toHex()));
        }
        return hexTopics;
    }

    // Blocks other than the anchor get a synthetic hash. Hashes are truncated to 32 bytes, as BLOCKHASH does.
    private record SimulatedBlock(String hash, long number) {

        SimulatedBlock(final RecordFile anchor, final long entryIndex, final long number) {
            this(blockHash(anchor, entryIndex, number), number);
        }

        private static String blockHash(final RecordFile anchor, final long entryIndex, final long number) {
            final var anchorHash = HEX_PREFIX + StringUtils.substring(anchor.getHash(), 0, 64);
            if (entryIndex == 0 && number == anchor.getIndex()) {
                return anchorHash;
            }
            final var seed = org.apache.tuweni.bytes.Bytes.concatenate(
                    org.apache.tuweni.bytes.Bytes.fromHexString(anchorHash),
                    org.apache.tuweni.bytes.Bytes.ofUnsignedLong(entryIndex));
            return Hash.keccak256(seed).toHexString();
        }
    }
}
