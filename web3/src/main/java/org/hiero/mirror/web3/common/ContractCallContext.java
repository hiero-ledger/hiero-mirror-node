// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
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

    /**
     * Lazily-computed cache of the resolved historical timestamp. {@code null} means not yet computed; once set to a
     * non-null value it is reused for all subsequent {@link #getTimestamp()} calls so that the entire request sees a
     * consistent timestamp even if the Hedera framework resets writable state between inner transactions.
     */
    @Getter(AccessLevel.NONE)
    private Optional<Long> cachedHistoricalTimestamp;

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

    /**
     * When enabled, contract storage reads return dummy values without database access so the EVM can record which
     * storage slots were accessed in {@link #readCache}.
     */
    @Setter
    private boolean storageDiscoveryMode;

    @Setter
    private boolean storageDiscoveryModeFinished;

    /**
     * Maps a contract id to the queue of storage slot keys discovered for it, preserving the order in which they were
     * searched. Slot keys searched first are consumed (and queried in the DB) first.
     */
    @Getter(AccessLevel.NONE)
    private final Map<EntityId, Queue<byte[]>> discoveredStorageSlotKeys = new HashMap<>();

    /**
     * The consensus timestamp at which the storage discovery pass was performed. This is set after discovery finishes
     * and is used in the execution pass to ensure discovered slot keys are only consumed by batch queries that match
     * the same timestamp mode (historical vs. latest). {@code null} means the discovery was done in the latest
     * (non-historical) context.
     */
    @Getter
    @Setter
    private Long discoveryBlockTimestamp;

    /**
     * Counts how many times {@code ContractStorageReadableKVState} resolved a slot against the backing store during the
     * execution (non-discovery) pass of the current request. Used to decide whether a repeated request accesses enough
     * storage slots to benefit from the storage discovery pass on subsequent invocations.
     */
    @Getter
    private int contractStorageReadCount;

    private ContractCallContext() {}

    /**
     * Increments the per-request contract storage read counter. Invoked from {@code ContractStorageReadableKVState}
     * whenever a slot is resolved against the backing store outside of the discovery pass.
     */
    public void incrementContractStorageReadCount() {
        contractStorageReadCount++;
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

    public Queue<byte[]> getDiscoveredStorageSlotKeys(final EntityId contractId) {
        return discoveredStorageSlotKeys.get(contractId);
    }

    /**
     * Copies storage slot keys discovered during the discovery pass, clears the per-request storage read cache so the
     * execution pass loads real values, and resets writable state from the discovery pass.
     */
    public void finishStorageDiscovery(final int storageStateId) {
        final var storageCache = readCache.get(storageStateId);
        if (storageCache != null) {
            for (final var key : storageCache.keySet()) {
                if (key instanceof SlotKey slotKey && slotKey.contractID() != null) {
                    final var contractId = EntityIdUtils.toEntityId(slotKey.contractID());
                    discoveredStorageSlotKeys
                            .computeIfAbsent(contractId, _ -> new LinkedList<>())
                            .add(slotKey.key().toByteArray());
                }
            }
            storageCache.clear();
        }
        reset();
        storageDiscoveryMode = false;
        storageDiscoveryModeFinished = true;
    }

    public boolean useHistorical() {
        return callServiceParameters != null && callServiceParameters.getBlock() != BlockType.LATEST;
    }

    /**
     * Returns the set timestamp or the consensus end timestamp from the set record file only if we are in a historical
     * context. For opcode replay, returns the explicitly set timestamp.
     *
     * <p>For historical (non-opcode) requests the resolved timestamp is cached on the first successful lookup so that
     * all state reads within the same request use a consistent value even if the Hedera framework resets writable state
     * between inner transactions.
     */
    public Optional<Long> getTimestamp() {
        if (opcodeContext != null) {
            return timestamp;
        }
        if (useHistorical()) {
            // Cache the resolved timestamp to guarantee consistency: once a real timestamp is available every
            // subsequent storage read in this request sees the same value, even if the Hedera framework resets
            // writable state (clearing the KV read-cache) between inner sub-transactions. Only cache non-empty
            // results so that if the RecordFile is not yet reachable on the first call, we retry on the next.
            if (cachedHistoricalTimestamp == null || cachedHistoricalTimestamp.isEmpty()) {
                final var resolved = getTimestampOrDefaultFromRecordFile();
                if (resolved.isPresent()) {
                    cachedHistoricalTimestamp = resolved;
                }
                return resolved;
            }
            return cachedHistoricalTimestamp;
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
