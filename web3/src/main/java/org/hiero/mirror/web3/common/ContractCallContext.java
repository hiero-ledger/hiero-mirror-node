// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.common;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.state.Utils;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.StateOverride;

@SuppressWarnings("deprecation")
@Getter
public class ContractCallContext {

    public static final String CONTEXT_NAME = "ContractCallContext";

    private static final ScopedValue<ContractCallContext> SCOPED_VALUE = ScopedValue.newInstance();

    /**
     * Record file indexes found to be absent during this call. Absent indexes are not cached across requests, so this
     * keeps repeated BLOCKHASH lookups of the same absent index to a single query per call.
     */
    private final Set<Long> missingBlockIndexes = new HashSet<>();

    @Getter(AccessLevel.NONE)
    private final StateCaches readCache = new StateCaches();

    @Getter
    private final long startTime = System.currentTimeMillis();

    @Getter(AccessLevel.NONE)
    private final StateCaches writeCache = new StateCaches();

    /** Successful simulation writes, retained only for this request. Null values are deletion tombstones. */
    @Getter(AccessLevel.NONE)
    private final Map<Integer, Map<Object, Object>> committedCache = new HashMap<>();

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

    /**
     * Throttle gas the controller returns once the request ends. A trace adds up the refunds of the transactions that
     * ran and the whole gas limit of those that did not.
     */
    @Setter
    private long refundableGas;

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
     * Number of synthetic accounts created for state overrides on addresses with no entity in the DB.
     */
    @Getter(AccessLevel.NONE)
    private long overriddenAccountCount;

    /**
     * Approximate serialized size of the trace data of the current execution.
     */
    @Setter
    private long traceDataBytes;

    /**
     * The current {@code block_override}. {@link BlockOverride#EMPTY} keeps the bound record file's block header and the
     * message frame values.
     */
    @Setter
    private BlockOverride blockOverride = BlockOverride.EMPTY;

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

    /**
     * Allocates a distinct, 1-based offset for each synthetic state-override account created within this request, so
     * that each one receives its own account number.
     */
    public long nextOverriddenAccountOffset() {
        return ++overriddenAccountCount;
    }

    public void reset() {
        writeCache.clear();
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
     * timestamp reads the record file's consensus end.
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
        return readCache.get(stateId);
    }

    public Map<Object, Object> getWriteCacheState(final int stateId) {
        return writeCache.get(stateId);
    }

    public Map<Object, Object> getCommittedCacheState(final int stateId) {
        return committedCache.computeIfAbsent(stateId, _ -> new HashMap<>());
    }

    /** Move pending writes into the committed overlay for later transactions in this request. */
    public void commitWrites(final int stateId) {
        final var pending = getWriteCacheState(stateId);
        final var committed = getCommittedCacheState(stateId);
        committed.putAll(pending);
        pending.clear();
    }

    public RecordFile getRecordFile() {
        return blockSupplier.get();
    }

    public boolean isDeadlineExceeded() {
        return deadlineMillis > 0 && remainingMillis(0L) <= 0L;
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
     * The cache of each state for one request. A state is looked up on every read and write of the state, and nearly
     * all state IDs are small, so those are found by index and only the few large ones in a map.
     */
    private static final class StateCaches {

        private static final int INDEXED_STATE_IDS = 128;

        @SuppressWarnings("unchecked")
        private final Map<Object, Object>[] indexed = new Map[INDEXED_STATE_IDS];

        private final Map<Integer, Map<Object, Object>> others = new HashMap<>();

        void clear() {
            Arrays.fill(indexed, null);
            others.clear();
        }

        Map<Object, Object> get(final int stateId) {
            if (stateId < 0 || stateId >= INDEXED_STATE_IDS) {
                return others.computeIfAbsent(stateId, _ -> new HashMap<>());
            }

            var cache = indexed[stateId];
            if (cache == null) {
                cache = new HashMap<>();
                indexed[stateId] = cache;
            }
            return cache;
        }
    }
}
