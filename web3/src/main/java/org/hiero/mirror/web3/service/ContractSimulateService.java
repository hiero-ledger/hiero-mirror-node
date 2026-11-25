// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.hiero.mirror.web3.ApiEndpointName.SIMULATE;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_SIMULATE;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.ContractID;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.rest.model.SimulateCallError;
import org.hiero.mirror.rest.model.SimulateCallResult;
import org.hiero.mirror.rest.model.SimulateLog;
import org.hiero.mirror.rest.model.SimulateResponse;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.evm.utils.EvmTokenUtils;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.state.Utils;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.hiero.mirror.web3.viewmodel.SimulateRequest;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.dao.QueryTimeoutException;

@Named
public class ContractSimulateService extends ContractCallService {

    // Error codes of Ethereum JSON-RPC clients for a reverted call and for any other failed execution.
    private static final int EXECUTION_ERROR_CODE = -32015;
    private static final int REVERT_ERROR_CODE = 3;

    private final RecordFileService recordFileService;
    private final Web3Properties web3Properties;

    public ContractSimulateService(
            EvmProperties evmProperties,
            MeterRegistry meterRegistry,
            RecordFileService recordFileService,
            ThrottleProperties throttleProperties,
            TransactionExecutionService transactionExecutionService,
            Web3Properties web3Properties) {
        super(meterRegistry, recordFileService, evmProperties, throttleProperties, transactionExecutionService);
        this.recordFileService = recordFileService;
        this.web3Properties = web3Properties;
    }

    /**
     * Runs the simulation in {@code context}, which must be the bound {@link ContractCallContext} holding the gas of all
     * calls as {@link ContractCallContext#getRefundableGas() refundable gas}. Afterwards it holds the refundable gas of
     * the executed calls plus the gas of the calls that did not run.
     */
    public SimulateResponse simulate(final SimulateRequest request, final ContractCallContext context) {
        return new SimulateResponse().result(runSimulation(request, context));
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
            final long transactionIndex) {
        context.setCallServiceParameters(params);
        final var result = doProcessCall(params, params.getGas(), false);
        final var transactionHash = syntheticTransactionHash(result, transactionIndex);
        final var contractLogs = mapLogs(result, block, logIndex, transactionIndex, transactionHash);
        final var transferLogs =
                mapTransferLogs(context, block, logIndex + contractLogs.size(), transactionIndex, transactionHash);
        final var logs = new ArrayList<SimulateLog>(contractLogs.size() + transferLogs.size());
        logs.addAll(contractLogs);
        logs.addAll(transferLogs);
        return new SimulateCallResult()
                .gasUsed(result.gasUsed())
                .logs(logs)
                .returnData(result.contractCallResult())
                .success(true);
    }

    private void failIfTimedOut(final ContractCallContext context) {
        final long elapsed = System.currentTimeMillis() - context.getStartTime();
        if (elapsed
                >= web3Properties.getApi(SIMULATE).getResponse().getTimeout().toMillis()) {
            throw new QueryTimeoutException("Transaction timed out after %s ms".formatted(elapsed));
        }
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
            logs.add(new SimulateLog()
                    .address(contractAddress(logInfo.contractID()).toHexString())
                    .blockHash(block.hash())
                    .blockNumber(block.number())
                    .data(Utils.withHexPrefix(logInfo.data().toHex()))
                    .index(index)
                    .topics(topics(logInfo.topic()))
                    .transactionHash(transactionHash)
                    .transactionIndex(transactionIndex));
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
        final var transferLogs = context.getTransferLogs();
        var index = startingLogIndex;
        for (final var log : transferLogs) {
            log.blockHash(block.hash())
                    .blockNumber(block.number())
                    .index(index)
                    .transactionHash(transactionHash)
                    .transactionIndex(transactionIndex);
            index++;
        }
        return List.copyOf(transferLogs);
    }

    private List<SimulateCallResult> runSimulation(final SimulateRequest request, final ContractCallContext context) {
        if (evmProperties.isSharedWritableState()) {
            // Otherwise writes flush into a cross-request cache shared by other users' calls.
            throw new IllegalStateException(
                    "hiero.mirror.web3.evm.sharedWritableState must be disabled to use /contracts/simulate.");
        }

        // Resolved once so every entry builds on the same anchor block.
        final var anchor =
                recordFileService.findByBlockType(request.getBlock()).orElseThrow(BlockNumberNotFoundException::new);

        context.setApi(SIMULATE);
        context.setBlockSupplier(() -> anchor);
        context.setTraceTransfers(request.isTraceTransfers());
        // The request has a single entry, validated by SimulateRequest.
        final var blockStateCall = request.getBlockStateCalls().getFirst();
        final var blockOverride = blockStateCall.getBlockOverride();
        context.setBlockOverride(blockOverride);
        context.applyStateOverrides(blockStateCall.getStateOverrides());

        final var blockNumber = blockOverride.blockNumber() != null ? blockOverride.blockNumber() : anchor.getIndex();
        final var block = new SimulatedBlock(anchor, blockNumber);
        final var results = new ArrayList<SimulateCallResult>();
        long logIndex = 0;
        long transactionIndex = 0;
        for (final var call : blockStateCall.getCalls()) {
            // A timeout inside the executor would otherwise surface as a failed call.
            failIfTimedOut(context);
            final var params = toExecutionParameters(request.getBlock(), call);
            updateGasLimitMetric(params);
            context.getTransferLogs().clear();
            context.getTransferLogSnapshots().clear();
            // Refundable gas of the earlier calls plus the gas of the calls that have not run yet.
            final long otherRefundableGas = context.getRefundableGas() - call.getGas();
            context.setRefundableGas(call.getGas());

            try {
                final var callResult = executeCall(params, context, block, logIndex, transactionIndex);
                results.add(callResult);
                logIndex += callResult.getLogs().size();
            } catch (final MirrorEvmTransactionException e) {
                final var partialResult = e.getResult();
                final var returnData = StringUtils.defaultIfEmpty(e.getData(), HEX_PREFIX);
                results.add(new SimulateCallResult()
                        .error(toCallError(e, returnData))
                        .gasUsed(partialResult != null ? partialResult.gasUsed() : 0L)
                        .logs(List.of())
                        .returnData(returnData)
                        .success(false));
            } finally {
                context.setRefundableGas(otherRefundableGas + context.getRefundableGas());
                context.commit();
            }

            transactionIndex++;
        }

        return results;
    }

    private String syntheticTransactionHash(final EvmTransactionResult result, final long transactionIndex) {
        final var seed = org.apache.tuweni.bytes.Bytes.concatenate(
                org.apache.tuweni.bytes.Bytes.fromHexString(result.contractCallResult()),
                org.apache.tuweni.bytes.Bytes.ofUnsignedLong(transactionIndex));
        return Hash.keccak256(seed).toHexString();
    }

    private SimulateCallError toCallError(final MirrorEvmTransactionException e, final String returnData) {
        if (!CONTRACT_REVERT_EXECUTED.protoName().equals(e.getMessage())) {
            return new SimulateCallError()
                    .code(EXECUTION_ERROR_CODE)
                    .data(HEX_PREFIX)
                    .message(e.getMessage());
        }
        final var message =
                StringUtils.isBlank(e.getDetail()) ? "execution reverted" : "execution reverted: " + e.getDetail();
        return new SimulateCallError().code(REVERT_ERROR_CODE).data(returnData).message(message);
    }

    private ContractExecutionParameters toExecutionParameters(final BlockType block, final ContractCallRequest call) {
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

    // An overridden block number gets a synthetic hash. Hashes are truncated to 32 bytes, as BLOCKHASH does.
    private record SimulatedBlock(String hash, long number) {

        SimulatedBlock(final RecordFile anchor, final long number) {
            this(blockHash(anchor, number), number);
        }

        private static String blockHash(final RecordFile anchor, final long number) {
            final var anchorHash = HEX_PREFIX + StringUtils.substring(anchor.getHash(), 0, 64);
            if (number == anchor.getIndex()) {
                return anchorHash;
            }
            final var seed = org.apache.tuweni.bytes.Bytes.concatenate(
                    org.apache.tuweni.bytes.Bytes.fromHexString(anchorHash),
                    org.apache.tuweni.bytes.Bytes.ofUnsignedLong(number));
            return Hash.keccak256(seed).toHexString();
        }
    }
}
