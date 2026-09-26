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
import org.hiero.mirror.web3.Web3Properties.ApiEndpointName;
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
    private static final ScopedValue<ContractCallContext> SCOPED_VALUE = ScopedValue.newInstance();

    @Getter(AccessLevel.NONE)
    private final Map<Integer, Map<Object, Object>> readCache = new HashMap<>();

    @Getter
    private final long startTime = System.currentTimeMillis();

    @Getter(AccessLevel.NONE)
    private final Map<Integer, Map<Object, Object>> writeCache = new HashMap<>();

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
        writeCache.clear();
    }

    public boolean useHistorical() {
        return callServiceParameters != null && callServiceParameters.getBlock() != BlockType.LATEST;
    }

    /**
     * Returns the set timestamp or the consensus end timestamp from the set record file only if we are in a historical
     * context. For opcode replay, returns the pre-transaction {@link #timestamp} so entity state is read as-of before
     * the replayed transaction.
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
        return readCache.computeIfAbsent(stateId, _ -> new HashMap<>());
    }

    public Map<Object, Object> getWriteCacheState(final int stateId) {
        return writeCache.computeIfAbsent(stateId, _ -> new HashMap<>());
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
            return;
        }
        final var addressToAccounts = new HashMap<Bytes, StateOverride>(overrides.size());
        for (final var stateOverride : overrides) {
            addressToAccounts.put(Bytes.wrap(Utils.parseHex(stateOverride.getAddress())), stateOverride);
        }
        this.stateOverrides = addressToAccounts;
    }

    /**
     * Applies HIP-1485 {@code block_override}. A set {@code number} becomes EVM {@code block.number}; a set {@code time}
     * becomes EVM {@code block.timestamp}.
     */
    public void applyBlockOverride(final @Nullable BlockOverride override) {
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
        } catch (NumberFormatException e) {
            throw new InvalidParametersException("Invalid block_override: " + e.getMessage());
        }
    }

    public long evmBlockNumber(final long fallback) {
        return blockOverrideNumber != null ? blockOverrideNumber : fallback;
    }

    public long evmBlockTimeNanos(final long fallback) {
        return blockOverrideTimeNanos != null ? blockOverrideTimeNanos : fallback;
    }
}
