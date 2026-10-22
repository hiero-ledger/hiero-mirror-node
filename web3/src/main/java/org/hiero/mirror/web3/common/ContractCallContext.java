// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.state.Utils;
import org.hiero.mirror.web3.utils.HexUtils;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("deprecation")
@Getter
public class ContractCallContext {

    public static final String CONTEXT_NAME = "ContractCallContext";

    /** Maximum new read-cache and write-cache entries one request may insert. */
    public static final int MAX_CACHE_ENTRIES = 100_000;

    private static final ScopedValue<ContractCallContext> SCOPED_VALUE = ScopedValue.newInstance();

    @Getter(AccessLevel.NONE)
    private final Map<Integer, Map<Object, Object>> readCache = new HashMap<>();

    @Getter
    private final long startTime = System.currentTimeMillis();

    @Getter(AccessLevel.NONE)
    private final Map<Integer, Map<Object, Object>> writeCache = new HashMap<>();

    private int cacheEntries;

    /** When set, cache puts update the maps without changing {@link #cacheEntries}. */
    private boolean countingSuspended;

    @Setter
    private ActionContext actionContext = null;

    /**
     * Optional API endpoint used to resolve a per-endpoint request timeout.
     */
    @Setter
    private ApiEndpointName api;

    @Setter
    private OpcodeContext opcodeContext = null;

    /**
     * Absolute epoch-millis deadline for this request. {@code 0} means fall back to the per-endpoint request timeout.
     */
    @Setter
    private long deadlineMillis;

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

    /**
     * Optional EVM {@code block.number} override from {@code block_override.number}. {@code null} means use the bound
     * record file.
     */
    @Setter
    private @Nullable Long blockOverrideNumber;

    /**
     * Optional EVM {@code block.timestamp} override from {@code block_override.time}, in nanoseconds since epoch.
     * {@code null} means use the bound record file.
     */
    @Setter
    private @Nullable Long blockOverrideTimeNanos;

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
                || callServiceParameters.getSender().getBytes().isZero()) {
            return false;
        }

        return callServiceParameters.getGasPrice() > 0 || callServiceParameters.getValue() > 0;
    }

    public void reset() {
        discardAll(writeCache);
        writeCache.clear();
    }

    /** Drops cached reads and writes so the next call does not observe the previous call's state. */
    public void clearCaches() {
        discardAll(readCache);
        discardAll(writeCache);
        readCache.clear();
        writeCache.clear();
    }

    /**
     * Drops one cached state so the next read reloads it. Other states stay, including account and storage changes
     * carried from an earlier trace bundle.
     */
    public void evictCachedState(final int stateId) {
        discard(readCache.remove(stateId));
        discard(writeCache.remove(stateId));
    }

    public boolean useHistorical() {
        return callServiceParameters != null && callServiceParameters.getBlock() != BlockType.LATEST;
    }

    /**
     * Returns the set timestamp or the consensus end timestamp from the set record file only if we are in a historical
     * context. For opcode replay, returns the pre-transaction {@link #timestamp} so entity state is read as-of before
     * the replayed transaction. An action trace returns {@link #timestamp} when one is set. {@code transaction_index}
     * {@code 0} starts at the beginning of the block. {@code transaction_index} {@code -1} starts after every
     * transaction in the block. The same timestamp applies to every bundle. A historical action trace with no
     * timestamp reads the record file's consensus end. {@code block_override.time} is not part of this timestamp.
     */
    public Optional<Long> getTimestamp() {
        if (opcodeContext != null) {
            return timestamp;
        }
        if (ApiEndpointName.ACTIONS.equals(getApi()) && timestamp.isPresent()) {
            return timestamp;
        }
        if (useHistorical()) {
            return getTimestampOrDefaultFromRecordFile();
        }
        return Optional.empty();
    }

    /**
     * Timestamp used for executor consensus time and system-file (exchange-rate / fee-schedule) loads. Prefers the
     * opcode-replay transaction consensus time when present so an hour-boundary txn is not rounded into the previous
     * hour; otherwise {@link #getTimestamp()}.
     */
    public Optional<Long> getTimestampForSystemFiles() {
        return getTimestamp().map(t -> t + 1);
    }

    private Optional<Long> getTimestampOrDefaultFromRecordFile() {
        return timestamp.or(() -> Optional.ofNullable(getRecordFile()).map(RecordFile::getConsensusEnd));
    }

    public Map<Object, Object> getReadCacheState(final int stateId) {
        return readCache.computeIfAbsent(stateId, _ -> new CountingMap());
    }

    public Map<Object, Object> getWriteCacheState(final int stateId) {
        return writeCache.computeIfAbsent(stateId, _ -> new CountingMap());
    }

    public RecordFile getRecordFile() {
        return blockSupplier.get();
    }

    public boolean isDeadlineExceeded() {
        return deadlineMillis > 0 && remainingMillis(0L) <= 0L;
    }

    /**
     * Counts one new cache entry. Fails the request once {@link #MAX_CACHE_ENTRIES} inserts have happened. Callers skip
     * this when the key is already present.
     */
    public void recordCacheInsert() {
        if (cacheEntries >= MAX_CACHE_ENTRIES) {
            throw new InvalidParametersException("Trace state cache exceeded the configured limit");
        }
        cacheEntries++;
    }

    /**
     * Milliseconds remaining until this request should stop work. Uses {@link #deadlineMillis} when set; otherwise
     * {@code startTime + fallbackTimeoutMillis}.
     */
    public long remainingMillis(final long fallbackTimeoutMillis) {
        final long deadline = deadlineMillis > 0 ? deadlineMillis : startTime + fallbackTimeoutMillis;
        return deadline - System.currentTimeMillis();
    }

    public void applyStateOverrides(final List<StateOverride> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            this.stateOverrides = null;
            return;
        }
        final var addressToAccounts = new HashMap<Bytes, StateOverride>(overrides.size());
        for (final var stateOverride : overrides) {
            addressToAccounts.put(Bytes.wrap(Utils.parseHex(stateOverride.getAddress())), stateOverride);
        }
        this.stateOverrides = addressToAccounts;
    }

    /**
     * Applies HIP-1485 {@code block_override}. A set {@code number} becomes EVM {@code block.number} and a set
     * {@code time} becomes EVM {@code block.timestamp}. Both may be set. A null override clears both.
     */
    public void applyBlockOverride(final @Nullable BlockOverride override) {
        blockOverrideNumber = null;
        blockOverrideTimeNanos = null;
        if (override == null) {
            return;
        }
        try {
            if (StringUtils.isNotBlank(override.getNumber())) {
                blockOverrideNumber = HexUtils.parseValue(override.getNumber());
            }
            if (StringUtils.isNotBlank(override.getTime())) {
                blockOverrideTimeNanos = DomainUtils.convertToNanosMax(HexUtils.parseValue(override.getTime()), 0);
            }
        } catch (final NumberFormatException _) {
            throw new InvalidParametersException("Invalid block_override");
        }
    }

    /**
     * Continues the previous bundle's block override when this bundle sets none. A set block number increases by 1. A
     * set block timestamp increases by 2 seconds. A field that was not set stays unset.
     */
    public void advanceBlockOverride() {
        try {
            if (blockOverrideNumber != null) {
                blockOverrideNumber = Math.addExact(blockOverrideNumber, 1L);
            }
            if (blockOverrideTimeNanos != null) {
                blockOverrideTimeNanos = Math.addExact(blockOverrideTimeNanos, DomainUtils.convertToNanosMax(2L, 0L));
            }
        } catch (ArithmeticException _) {
            throw new InvalidParametersException("Invalid block_override: block context overflow");
        }
    }

    public long evmBlockNumber(final long fallback) {
        return blockOverrideNumber != null ? blockOverrideNumber : fallback;
    }

    public long evmBlockTimeNanos(final long fallback) {
        return blockOverrideTimeNanos != null ? blockOverrideTimeNanos : fallback;
    }

    /** Whether a previous bundle left an EVM block number or timestamp in effect. */
    public boolean hasBlockOverride() {
        return blockOverrideNumber != null || blockOverrideTimeNanos != null;
    }

    /**
     * Copies of the read and write caches taken before one trace transaction. The maps are independent of the live
     * caches.
     */
    public record CacheSnapshot(
            Map<Integer, Map<Object, Object>> readCache, Map<Integer, Map<Object, Object>> writeCache) {}

    public CacheSnapshot captureCaches() {
        return new CacheSnapshot(copyCache(readCache), copyCache(writeCache));
    }

    /** Replaces both live caches with the snapshot. Used when a trace transaction reverts or throws. */
    public void restoreCaches(final CacheSnapshot snapshot) {
        replaceCache(readCache, snapshot.readCache());
        replaceCache(writeCache, snapshot.writeCache());
        recountCacheEntries();
    }

    /**
     * After a successful call, put back keys a stack {@code reset()} dropped. A key that is still present, including a
     * null tombstone for a delete, stays as the call left it.
     */
    public void retainDroppedWrites(final CacheSnapshot snapshot) {
        countingSuspended = true;
        try {
            for (final var state : snapshot.writeCache().entrySet()) {
                final var live = writeCache.computeIfAbsent(state.getKey(), _ -> new CountingMap());
                for (final var entry : state.getValue().entrySet()) {
                    if (!live.containsKey(entry.getKey())) {
                        live.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        } finally {
            countingSuspended = false;
            recountCacheEntries();
        }
    }

    private static Map<Integer, Map<Object, Object>> copyCache(final Map<Integer, Map<Object, Object>> source) {
        final var copy = HashMap.<Integer, Map<Object, Object>>newHashMap(source.size());
        for (final var entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    private void replaceCache(
            final Map<Integer, Map<Object, Object>> target, final Map<Integer, Map<Object, Object>> snapshot) {
        target.clear();
        for (final var entry : snapshot.entrySet()) {
            final var map = new CountingMap();
            map.copyFrom(entry.getValue());
            target.put(entry.getKey(), map);
        }
    }

    private void discardAll(final Map<Integer, Map<Object, Object>> cache) {
        for (final var state : cache.values()) {
            discard(state);
        }
    }

    private void discard(final @Nullable Map<Object, Object> state) {
        if (state != null) {
            cacheEntries = Math.max(0, cacheEntries - state.size());
        }
    }

    private void recountCacheEntries() {
        var total = 0;
        for (final var state : readCache.values()) {
            total += state.size();
        }
        for (final var state : writeCache.values()) {
            total += state.size();
        }
        if (total > MAX_CACHE_ENTRIES) {
            cacheEntries = total;
            throw new InvalidParametersException("Trace state cache exceeded the configured limit");
        }
        cacheEntries = total;
    }

    /**
     * Counts a new key in {@link #cacheEntries}. Restored snapshots copy through {@link #copyFrom} so a revert does not
     * count the same keys again.
     */
    private final class CountingMap extends HashMap<Object, Object> {

        @Override
        public Object put(final Object key, final Object value) {
            noteNewKey(key);
            return super.put(key, value);
        }

        @Override
        public void putAll(final Map<?, ?> entries) {
            for (final var entry : entries.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public Object remove(final Object key) {
            if (containsKey(key)) {
                cacheEntries = Math.max(0, cacheEntries - 1);
            }
            return super.remove(key);
        }

        @Override
        public void clear() {
            cacheEntries = Math.max(0, cacheEntries - size());
            super.clear();
        }

        private void copyFrom(final Map<Object, Object> source) {
            final var previous = countingSuspended;
            countingSuspended = true;
            try {
                super.putAll(source);
            } finally {
                countingSuspended = previous;
            }
        }

        private void noteNewKey(final Object key) {
            if (countingSuspended || containsKey(key)) {
                return;
            }
            recordCacheInsert();
        }
    }
}
