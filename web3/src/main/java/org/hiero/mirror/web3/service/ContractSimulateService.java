// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.service.entityid.impl.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_ID;
import static org.hiero.mirror.web3.Web3Properties.ApiEndpointName.SIMULATE;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_SIMULATE;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.ContractID;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.evm.utils.EvmTokenUtils;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
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

    private final RecordFileService recordFileService;

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
        this.recordFileService = recordFileService;
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

    private String addressTopic(final Address address) {
        return org.apache.tuweni.bytes.Bytes32.leftPad(address.getBytes()).toHexString();
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

    // Entity numbers keep advancing across entries, so the entity-id write buffer survives the reset. The read cache
    // is cleared too, since it holds block singletons built for the previous entry's block number.
    private void resetToAnchorState(final ContractCallContext context) {
        final var entityIdWrites = new HashMap<>(context.getWriteCacheState(ENTITY_ID_STATE_ID));
        context.reset();
        context.clearReadCache();
        context.getWriteCacheState(ENTITY_ID_STATE_ID).putAll(entityIdWrites);
    }

    private List<SimulateCallResult> runSimulation(final SimulateRequest request, final AtomicLong remainingGas) {
        if (evmProperties.isSharedWritableState()) {
            // Otherwise writes flush into a cross-request cache shared by other users' calls.
            throw new IllegalStateException(
                    "hiero.mirror.web3.evm.sharedWritableState must be disabled to use /contracts/simulate.");
        }

        // Resolved once so a new "latest" block arriving mid-request cannot shift the numbering between entries.
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

            for (final var blockCall : request.getBlockStateCalls()) {
                if (entryIndex > 0) {
                    resetToAnchorState(context);
                }
                context.setBlockOverrideNumber(anchor.getIndex() + entryIndex);
                if (!blockCall.getStateOverrides().isEmpty()) {
                    context.getStateOverrides().putAll(Utils.toOverrideMap(blockCall.getStateOverrides()));
                    context.clearReadCache();
                }

                final var block = new SimulatedBlock(anchor, entryIndex);
                long logIndex = 0;
                long transactionIndex = 0;
                for (final var call : blockCall.getCalls()) {
                    final var params = toExecutionParameters(request.getBlock(), call);
                    final var callSnapshot = context.snapshotWriteCache();
                    context.getCapturedTransfers().clear();
                    context.getTransferFrameStarts().clear();
                    remainingGas.addAndGet(-call.getGas());

                    try {
                        final var callResult =
                                executeCall(params, context, block, logIndex, transactionIndex, requestCallIndex);
                        results.add(callResult);
                        logIndex += callResult.logs().size();
                    } catch (MirrorEvmTransactionException e) {
                        context.restoreWriteCache(callSnapshot);
                        final var partialResult = e.getResult();
                        results.add(new SimulateCallResult(
                                Utils.toHex(partialResult != null ? partialResult.gasUsed() : 0L),
                                List.of(),
                                StringUtils.defaultIfEmpty(e.getData(), HEX_PREFIX),
                                REVERT_STATUS));
                    } catch (InvalidInputException | DataAccessException e) {
                        // Request-level errors (e.g. unknown block) and infrastructure failures fail the whole request.
                        throw e;
                    } catch (RuntimeException e) {
                        log.error("Unexpected error simulating call", e);
                        context.restoreWriteCache(callSnapshot);
                        results.add(new SimulateCallResult(Utils.toHex(0L), List.of(), HEX_PREFIX, REVERT_STATUS));
                    }

                    transactionIndex++;
                    requestCallIndex++;
                }

                entryIndex++;
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

    /**
     * Each block_state_calls entry is reported as its own block following the anchor: entry 0 is the anchor block
     * itself, and entry i is block anchor + i with a synthetic hash derived from the anchor hash. Hashes are 32 bytes,
     * matching the BLOCKHASH opcode's truncation of the 48-byte record file hash.
     */
    private record SimulatedBlock(String hash, long number) {

        SimulatedBlock(final RecordFile anchor, final long entryIndex) {
            this(blockHash(anchor, entryIndex), anchor.getIndex() + entryIndex);
        }

        private static String blockHash(final RecordFile anchor, final long entryIndex) {
            final var anchorHash = HEX_PREFIX + StringUtils.substring(anchor.getHash(), 0, 64);
            if (entryIndex == 0) {
                return anchorHash;
            }
            final var seed = org.apache.tuweni.bytes.Bytes.concatenate(
                    org.apache.tuweni.bytes.Bytes.fromHexString(anchorHash),
                    org.apache.tuweni.bytes.Bytes.ofUnsignedLong(entryIndex));
            return Hash.keccak256(seed).toHexString();
        }
    }
}
