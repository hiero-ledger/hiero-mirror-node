// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import com.hedera.hapi.node.state.common.EntityNumber;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.Opcode;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.viewmodel.BlockType;

@SuppressWarnings("deprecation")
@Getter
public class ContractCallContext {

    public static final String CONTEXT_NAME = "ContractCallContext";
    private static final ScopedValue<ContractCallContext> SCOPED_VALUE = ScopedValue.newInstance();

    @Getter(AccessLevel.NONE)
    private final Map<Integer, Map<Object, Object>> readCache = new HashMap<>();

    @Getter
    private final long startTime = System.currentTimeMillis();

    @Getter(AccessLevel.NONE)
    private final Map<Integer, Map<Object, Object>> writeCache = new HashMap<>();

    @Setter
    private List<ContractAction> contractActions = List.of();

    @Setter
    private OpcodeTracerOptions opcodeTracerOptions;

    @Setter
    private List<Opcode> opcodes = new ArrayList<>();

    @Setter
    private CallServiceParameters callServiceParameters;

    /**
     * Record file which stores the block timestamp and other historical block details used for filtering of historical
     * data.
     */
    @Setter
    private RecordFile recordFile;

    @Setter
    private EntityNumber entityNumber;

    /**
     * The timestamp used to fetch the state from the stackedStateFrames.
     */
    @Setter
    private Optional<Long> timestamp = Optional.empty();

    @Setter
    private boolean isBalanceCall;

    @Setter
    private long gasRequirement;

    private ContractCallContext() {}

    /**
     * Determines if payer balance validation should be performed.
     * Balance validation is enabled when either gasPrice or value is greater than zero,
     * and a valid sender is provided.
     *
     * @return true if balance validation should be performed, false otherwise
     */
    public boolean validatePayerBalance() {
        if (callServiceParameters == null
                || callServiceParameters.getSender() == null
                || callServiceParameters.getSender().isZero()) {
            return false;
        }

        return callServiceParameters.getGasPrice() > 0 || callServiceParameters.getValue() > 0;
    }

    public static ContractCallContext get() {
        return SCOPED_VALUE.get();
    }

    public static boolean isInitialized() {
        return SCOPED_VALUE.isBound();
    }

    /**
     * Safe helper to check if the current context is a balance call without throwing when unbound.
     */
    public static boolean isBalanceCallSafe() {
        return SCOPED_VALUE.isBound() && SCOPED_VALUE.get().isBalanceCall();
    }

    public static <T> T run(Function<ContractCallContext, T> function) {
        return ScopedValue.getWhere(SCOPED_VALUE, new ContractCallContext(), () -> function.apply(SCOPED_VALUE.get()));
    }

    public void reset() {
        writeCache.clear();
    }

    public void addOpcodes(Opcode opcode) {
        opcodes.add(opcode);
    }

    public boolean useHistorical() {
        if (callServiceParameters != null) {
            return callServiceParameters.getBlock() != BlockType.LATEST;
        }
        return recordFile != null; // Remove recordFile comparison after mono code deletion
    }

    /**
     * Returns the set timestamp or the consensus end timestamp from the set record file only if we are in a historical
     * context. If not - an empty optional is returned.
     */
    public Optional<Long> getTimestamp() {
        if (useHistorical()) {
            return getTimestampOrDefaultFromRecordFile();
        }
        return Optional.empty();
    }

    private Optional<Long> getTimestampOrDefaultFromRecordFile() {
        return timestamp.or(() -> Optional.ofNullable(recordFile).map(RecordFile::getConsensusEnd));
    }

    public Map<Object, Object> getReadCacheState(final int stateId) {
        return readCache.computeIfAbsent(stateId, k -> new HashMap<>());
    }

    public Map<Object, Object> getWriteCacheState(final int stateId) {
        return writeCache.computeIfAbsent(stateId, k -> new HashMap<>());
    }
}
