// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.state.Utils.parseHex;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.evm.utils.EvmTokenUtils;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
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
        if (evmProperties.isSharedWritableState()) {
            // Otherwise writes flush into a cross-request cache shared by other users' calls.
            throw new IllegalStateException(
                    "hiero.mirror.web3.evm.sharedWritableState must be disabled to use /contracts/simulate.");
        }

        final var results = ContractCallContext.run(context -> {
            context.setTraceTransfers(request.isTraceTransfers());
            final List<List<SimulateCallResult>> entryResults =
                    new ArrayList<>(request.getBlockStateCalls().size());
            final var activeOverrides = new HashMap<Bytes, StateOverride>();
            context.setStateOverrides(activeOverrides);
            // Restored before every entry but the first, so real effects don't cross entry boundaries.
            final var anchorSnapshot = context.snapshotWriteCache();
            // Seeds the synthetic transaction hash; transaction_index resets per entry and would collide.
            long requestCallIndex = 0;

            for (final var entry : request.getBlockStateCalls()) {
                if (!entryResults.isEmpty()) {
                    context.restoreWriteCache(anchorSnapshot);
                }
                if (!entry.getStateOverrides().isEmpty()) {
                    activeOverrides.putAll(toOverrideMap(entry.getStateOverrides()));
                    context.clearReadCache();
                }

                final List<SimulateCallResult> callResults =
                        new ArrayList<>(entry.getCalls().size());
                long logIndex = 0;
                long transactionIndex = 0;
                for (final var call : entry.getCalls()) {
                    final var params = toExecutionParameters(request.getBlock(), call);
                    final var callSnapshot = context.snapshotWriteCache();
                    context.getCapturedTransfers().clear();

                    try {
                        final var result = callContract(params, context);
                        final var transactionHash = syntheticTransactionHash(result, requestCallIndex);
                        final var contractLogs = mapLogs(result, context, logIndex, transactionIndex, transactionHash);
                        final var transferLogs = mapTransferLogs(
                                context, logIndex + contractLogs.size(), transactionIndex, transactionHash);
                        final var logs = new ArrayList<SimulateLog>(contractLogs.size() + transferLogs.size());
                        logs.addAll(contractLogs);
                        logs.addAll(transferLogs);
                        logIndex += logs.size();
                        callResults.add(new SimulateCallResult(
                                toHex(result.gasUsed()), logs, result.contractCallResult(), SUCCESS_STATUS));
                    } catch (MirrorEvmTransactionException e) {
                        context.restoreWriteCache(callSnapshot);
                        context.getCapturedTransfers().clear();
                        final var partialResult = e.getResult();
                        callResults.add(new SimulateCallResult(
                                toHex(partialResult != null ? partialResult.gasUsed() : 0L),
                                List.of(),
                                Objects.requireNonNullElse(e.getData(), HEX_PREFIX),
                                REVERT_STATUS));
                    }

                    transactionIndex++;
                    requestCallIndex++;
                }

                entryResults.add(callResults);
            }

            return entryResults;
        });

        return new SimulateResponse(results);
    }

    private static Map<Bytes, StateOverride> toOverrideMap(final List<StateOverride> overrides) {
        final var map = new HashMap<Bytes, StateOverride>(overrides.size());
        for (final var override : overrides) {
            map.put(Bytes.wrap(parseHex(override.getAddress())), override);
        }
        return map;
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

    private List<SimulateLog> mapLogs(
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
        final var transactionIndexHex = toHex(transactionIndex);

        final var logs = new ArrayList<SimulateLog>(functionResult.logInfo().size());
        var index = startingLogIndex;
        for (final var logInfo : functionResult.logInfo()) {
            logs.add(new SimulateLog(
                    contractAddress(logInfo.contractID()).toHexString(),
                    blockHash,
                    blockNumber,
                    withHexPrefix(logInfo.data().toHex()),
                    toHex(index),
                    false,
                    logInfo.topic().stream()
                            .map(topic -> withHexPrefix(topic.toHex()))
                            .toList(),
                    transactionHash,
                    transactionIndexHex));
            index++;
        }
        return logs;
    }

    private List<SimulateLog> mapTransferLogs(
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
        final var transactionIndexHex = toHex(transactionIndex);

        final var logs = new ArrayList<SimulateLog>(transfers.size());
        var index = startingLogIndex;
        for (final var transfer : transfers) {
            logs.add(new SimulateLog(
                    TRANSFER_EVENT_EMITTER.toHexString(),
                    blockHash,
                    blockNumber,
                    transfer.value().toHexString(),
                    toHex(index),
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
        return recordFile != null ? withHexPrefix(recordFile.getHash()) : null;
    }

    private static String blockNumber(final ContractCallContext context) {
        final var recordFile = context.getRecordFile();
        return recordFile != null ? toHex(recordFile.getIndex()) : null;
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

    private static String toHex(final long value) {
        return HEX_PREFIX + Long.toHexString(value);
    }

    private static String withHexPrefix(final String hex) {
        if (hex == null) {
            return null;
        }
        return hex.startsWith(HEX_PREFIX) ? hex : HEX_PREFIX + hex;
    }
}
