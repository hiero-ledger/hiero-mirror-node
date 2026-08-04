// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.SIMULATE;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.ContractID;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.evm.utils.EvmTokenUtils;
import org.hiero.mirror.web3.exception.InvalidInputException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.state.Utils;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.SimulateCall;
import org.hiero.mirror.web3.viewmodel.SimulateCallResult;
import org.hiero.mirror.web3.viewmodel.SimulateLog;
import org.hiero.mirror.web3.viewmodel.SimulateRequest;
import org.hiero.mirror.web3.viewmodel.SimulateResponse;
import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.dao.DataAccessException;

@Named
@CustomLog
public class ContractSimulateService extends ContractCallService {

    private static final String REVERT_STATUS = "0x0";
    private static final String SUCCESS_STATUS = "0x1";

    /**
     * Emitter address for {@code trace_transfers} synthetic Transfer logs, per the HIP-1485 example.
     */
    private static final Address TRANSFER_EVENT_EMITTER =
            Address.fromHexString("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

    private static final String TRANSFER_EVENT_TOPIC0 = Hash.keccak256(org.apache.tuweni.bytes.Bytes.wrap(
                    "Transfer(address,address,uint256)".getBytes(StandardCharsets.UTF_8)))
            .toHexString();

    public ContractSimulateService(
            EvmProperties evmProperties,
            MeterRegistry meterRegistry,
            RecordFileService recordFileService,
            ThrottleManager throttleManager,
            ThrottleProperties throttleProperties,
            TransactionExecutionService transactionExecutionService) {
        super(
                throttleManager,
                throttleProperties,
                meterRegistry,
                recordFileService,
                evmProperties,
                transactionExecutionService);
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

    private List<List<SimulateCallResult>> runSimulation(final SimulateRequest request, final AtomicLong remainingGas) {
        if (evmProperties.isSharedWritableState()) {
            // Otherwise writes flush into a cross-request cache shared by other users' calls.
            throw new IllegalStateException(
                    "hiero.mirror.web3.evm.sharedWritableState must be disabled to use /contracts/simulate.");
        }

        return ContractCallContext.run(context -> {
            context.setSimulate(true);
            context.setApi(SIMULATE);
            context.setTraceTransfers(request.isTraceTransfers());
            context.setStateOverrides(new HashMap<>());
            final var entryResults = new ArrayList<List<SimulateCallResult>>(
                    request.getBlockStateCalls().size());
            // Seeds the synthetic transaction hash; transaction_index resets per entry and would collide.
            long requestCallIndex = 0;

            for (final var blockCall : request.getBlockStateCalls()) {
                if (!entryResults.isEmpty()) {
                    context.reset();
                }
                if (!blockCall.getStateOverrides().isEmpty()) {
                    context.getStateOverrides().putAll(Utils.toOverrideMap(blockCall.getStateOverrides()));
                    context.clearReadCache();
                }

                final var callResults =
                        new ArrayList<SimulateCallResult>(blockCall.getCalls().size());
                long logIndex = 0;
                long transactionIndex = 0;
                for (final var call : blockCall.getCalls()) {
                    final var params = toExecutionParameters(request.getBlock(), call);
                    final var callSnapshot = context.snapshotWriteCache();
                    context.getCapturedTransfers().clear();
                    remainingGas.addAndGet(-call.getGas());

                    try {
                        final var callResult =
                                executeCall(params, context, logIndex, transactionIndex, requestCallIndex);
                        callResults.add(callResult);
                        logIndex += callResult.logs().size();
                    } catch (MirrorEvmTransactionException e) {
                        context.restoreWriteCache(callSnapshot);
                        final var partialResult = e.getResult();
                        callResults.add(new SimulateCallResult(
                                Utils.toHex(partialResult != null ? partialResult.gasUsed() : 0L),
                                List.of(),
                                Objects.requireNonNullElse(e.getData(), HEX_PREFIX),
                                REVERT_STATUS));
                    } catch (InvalidInputException | DataAccessException e) {
                        // Request-level errors (e.g. unknown block) and infrastructure failures fail the whole request.
                        throw e;
                    } catch (RuntimeException e) {
                        log.error("Unexpected error simulating call", e);
                        context.restoreWriteCache(callSnapshot);
                        callResults.add(new SimulateCallResult(Utils.toHex(0L), List.of(), HEX_PREFIX, REVERT_STATUS));
                    }

                    transactionIndex++;
                    requestCallIndex++;
                }

                entryResults.add(callResults);
            }

            return entryResults;
        });
    }

    private SimulateCallResult executeCall(
            final ContractExecutionParameters params,
            final ContractCallContext context,
            final long logIndex,
            final long transactionIndex,
            final long requestCallIndex) {
        final var result = callContract(params, context);
        final var transactionHash = syntheticTransactionHash(result, requestCallIndex);
        final var contractLogs = mapLogs(result, context, logIndex, transactionIndex, transactionHash);
        final var transferLogs =
                mapTransferLogs(context, logIndex + contractLogs.size(), transactionIndex, transactionHash);
        final var logs = new ArrayList<SimulateLog>(contractLogs.size() + transferLogs.size());
        logs.addAll(contractLogs);
        logs.addAll(transferLogs);
        return new SimulateCallResult(Utils.toHex(result.gasUsed()), logs, result.contractCallResult(), SUCCESS_STATUS);
    }

    private static ContractExecutionParameters toExecutionParameters(final BlockType block, final SimulateCall call) {
        final var sender = call.getFrom() != null ? Address.fromHexString(call.getFrom()) : Address.ZERO;
        final var receiver = StringUtils.isNotEmpty(call.getTo()) ? Address.fromHexString(call.getTo()) : Address.ZERO;
        final var data = call.getData() != null ? call.getData() : HEX_PREFIX;

        return ContractExecutionParameters.builder()
                .block(block)
                .callData(hexToBytes(data))
                .callType(ETH_CALL)
                .gas(call.getGas())
                .gasPrice(call.getGasPrice())
                .isEstimate(false)
                .isStatic(false)
                .receiver(receiver)
                .sender(sender)
                .value(call.getValue())
                .build();
    }

    private static List<SimulateLog> mapLogs(
            final EvmTransactionResult result,
            final ContractCallContext context,
            final long startingLogIndex,
            final long transactionIndex,
            final String transactionHash) {
        final var functionResult = result.functionResult();
        if (functionResult == null || functionResult.logInfo().isEmpty()) {
            return List.of();
        }

        final var blockHash = blockHash(context);
        final var blockNumber = blockNumber(context);
        final var transactionIndexHex = Utils.toHex(transactionIndex);

        final var logs = new ArrayList<SimulateLog>(functionResult.logInfo().size());
        var index = startingLogIndex;
        for (final var logInfo : functionResult.logInfo()) {
            logs.add(new SimulateLog(
                    contractAddress(logInfo.contractID()).toHexString(),
                    blockHash,
                    blockNumber,
                    Utils.withHexPrefix(logInfo.data().toHex()),
                    Utils.toHex(index),
                    false,
                    logInfo.topic().stream()
                            .map(topic -> Utils.withHexPrefix(topic.toHex()))
                            .toList(),
                    transactionHash,
                    transactionIndexHex));
            index++;
        }
        return logs;
    }

    private static List<SimulateLog> mapTransferLogs(
            final ContractCallContext context,
            final long startingLogIndex,
            final long transactionIndex,
            final String transactionHash) {
        final var transfers = context.getCapturedTransfers();
        if (transfers.isEmpty()) {
            return List.of();
        }

        final var blockHash = blockHash(context);
        final var blockNumber = blockNumber(context);
        final var transactionIndexHex = Utils.toHex(transactionIndex);

        final var logs = new ArrayList<SimulateLog>(transfers.size());
        var index = startingLogIndex;
        for (final var transfer : transfers) {
            logs.add(new SimulateLog(
                    TRANSFER_EVENT_EMITTER.toHexString(),
                    blockHash,
                    blockNumber,
                    transfer.value().toHexString(),
                    Utils.toHex(index),
                    false,
                    List.of(TRANSFER_EVENT_TOPIC0, addressTopic(transfer.from()), addressTopic(transfer.to())),
                    transactionHash,
                    transactionIndexHex));
            index++;
        }
        return logs;
    }

    private static String blockHash(final ContractCallContext context) {
        final var recordFile = context.getRecordFile();
        return recordFile != null ? Utils.withHexPrefix(recordFile.getHash()) : null;
    }

    private static String blockNumber(final ContractCallContext context) {
        final var recordFile = context.getRecordFile();
        return recordFile != null ? Utils.toHex(recordFile.getIndex()) : null;
    }

    private static String addressTopic(final Address address) {
        return org.apache.tuweni.bytes.Bytes32.leftPad(address).toHexString();
    }

    private static Address contractAddress(final ContractID contractID) {
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

    private static String syntheticTransactionHash(final EvmTransactionResult result, final long requestCallIndex) {
        final var seed = org.apache.tuweni.bytes.Bytes.concatenate(
                org.apache.tuweni.bytes.Bytes.fromHexString(result.contractCallResult()),
                org.apache.tuweni.bytes.Bytes.ofUnsignedLong(requestCallIndex));
        return Hash.keccak256(seed).toHexString();
    }
}
