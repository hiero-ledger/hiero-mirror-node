// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.CapturedTransfer;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.StateOverride;

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
    private OpcodeContext opcodeContext = null;

    @Setter
    private CallServiceParameters callServiceParameters;

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

    @Setter
    private Supplier<RecordFile> blockSupplier = () -> null;

    /**
     * Per-address state overrides for the current call.
     */
    @Setter
    private Map<Bytes, StateOverride> stateOverrides;

    @Setter
    private boolean simulate;

    @Setter
    private boolean traceTransfers;

    private final List<CapturedTransfer> capturedTransfers = new ArrayList<>();

    private ContractCallContext() {}

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

    @SneakyThrows
    public static <T> T run(Function<ContractCallContext, T> function) {
        return ScopedValue.where(SCOPED_VALUE, new ContractCallContext())
                .call(() -> function.apply(SCOPED_VALUE.get()));
    }

    /**
     * Determines if payer balance validation should be performed. Balance validation is enabled when either gasPrice or
     * value is greater than zero, and a valid sender is provided.
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

    public void reset() {
        writeCache.clear();
    }

    /**
     * Deep copy of the current write cache, so a later call can be rolled back without losing earlier calls' state.
     */
    public Map<Integer, Map<Object, Object>> snapshotWriteCache() {
        final var snapshot = new HashMap<Integer, Map<Object, Object>>(writeCache.size());
        writeCache.forEach((stateId, cache) -> snapshot.put(stateId, new HashMap<>(cache)));
        return snapshot;
    }

    public void restoreWriteCache(final Map<Integer, Map<Object, Object>> snapshot) {
        writeCache.clear();
        snapshot.forEach((stateId, cache) -> writeCache.put(stateId, new HashMap<>(cache)));
    }

    /**
     * Reads through {@link #stateOverrides} are memoized here, so this must be cleared whenever the active
     * overrides change - otherwise a later read could still return a value from an override no longer in effect.
     */
    public void clearReadCache() {
        readCache.clear();
    }

    public boolean useHistorical() {
        return callServiceParameters != null && callServiceParameters.getBlock() != BlockType.LATEST;
    }

    /**
     * Returns the set timestamp or the consensus end timestamp from the set record file only if we are in a historical
     * context. For opcode replay, returns the explicitly set timestamp.
     */
    public Optional<Long> getTimestamp() {
        if (opcodeContext != null) {
            return timestamp;
        }
        if (useHistorical()) {
            return getTimestampOrDefaultFromRecordFile();
        }
        return Optional.empty();
    }

    private Optional<Long> getTimestampOrDefaultFromRecordFile() {
        return timestamp.or(() -> Optional.ofNullable(getRecordFile()).map(RecordFile::getConsensusEnd));
    }

    public Map<Object, Object> getReadCacheState(final int stateId) {
        return readCache.computeIfAbsent(stateId, _ -> new HashMap<>());
    }

    public Map<Object, Object> getWriteCacheState(final int stateId) {
        return writeCache.computeIfAbsent(stateId, _ -> new HashMap<>());
    }

    public RecordFile getRecordFile() {
        return blockSupplier.get();
    }
}
