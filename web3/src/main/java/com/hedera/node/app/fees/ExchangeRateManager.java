// SPDX-License-Identifier: Apache-2.0

package com.hedera.node.app.fees;

import static java.math.BigInteger.valueOf;
import static java.util.Objects.requireNonNull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.schemas.V0490FeeSchema;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.util.FileUtilities;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.RatesConfig;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.LongStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * Mirror Node override of the upstream exchange-rate manager from the pinned consensus-node dependency.
 *
 * <p>Upstream keeps one mutable active-rate reference per manager and expects access on the handle thread. Mirror
 * executors are reused across concurrent latest, historical, and opcode-replay calls, so this override changes rate
 * selection and publication as follows:
 *
 * <ul>
 *   <li>{@link #init(State, Bytes, ExchangeRateSet)} retains the supplied state for subsequent historical file loads.
 *       {@link #exchangeRates()}, {@link #activeRate(Instant)}, and {@link #exchangeRateInfo(State)} select historical
 *       rates whenever {@link ContractCallContext#getTimestampForSystemFiles()} supplies a timestamp, including
 *       timestamps within the current hour. The first two methods use the retained state; the third uses its argument.
 *   <li>Historical {@link ExchangeRateInfo} values are shared across all production managers in one cache, keyed by
 *       the system-file timestamp rounded down to the hour. Entries expire five minutes after insertion, regardless
 *       of reads, and the cache holds at most 1000 entries. Concurrent misses for the same hour share one load.
 *   <li>Cache misses load and validate the exchange-rate file through the mirror state and its
 *       {@link org.hiero.mirror.web3.state.SystemFileLoader}. Loading remains synchronous on the calling thread so the
 *       execution's scoped context determines the file timestamp. Missing, malformed, or invalid historical files
 *       fall back to the manager's latest valid rates without caching that fallback; other load failures propagate.
 *   <li>The latest immutable rate information is published through a volatile reference so scheduled
 *       {@code ExchangeRateUpdater} calls to {@link #systemUpdate(Bytes)} are visible to concurrent readers.
 *       Historical reads never replace the latest rates or modify midnight rates, and require no restore operation.
 * </ul>
 *
 * <p>Without an explicit system-file timestamp, {@code exchangeRates()} and {@code activeRate(Instant)} use the
 * latest published rates, while {@code exchangeRateInfo(State)} retains upstream's uncached read from the supplied
 * state. A package-private constructor accepts an isolated cache for tests; the public injected constructor always
 * uses the shared cache.
 *
 * <p>The upstream public API, update authorization and intraday limits, midnight-rate updates, and fee-conversion
 * arithmetic are preserved. Historical loads reuse upstream's mandatory-field and positive-rate validation.
 * {@code activeRate(Instant)} still selects the current rate before its expiration and the next rate at or after
 * expiration, continuing to use the next rate even after its expiration. Keep the remaining upstream logic aligned
 * with consensus-node dependency upgrades.
 */
@Singleton
public final class ExchangeRateManager {
    private static final Logger log = LogManager.getLogger(ExchangeRateManager.class);

    private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private static final long NANOS_PER_HOUR = 3_600_000_000_000L;
    static final Cache<Long, ExchangeRateInfo> HISTORICAL_RATES = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1000)
            .build();

    private final ConfigProvider configProvider;
    private final Cache<Long, ExchangeRateInfo> historicalRates;
    private volatile ExchangeRateInfo currentExchangeRateInfo;
    private State state;
    private ExchangeRateSet midnightRates;

    @Inject
    public ExchangeRateManager(@NonNull final ConfigProvider configProvider) {
        this(configProvider, HISTORICAL_RATES);
    }

    ExchangeRateManager(final ConfigProvider configProvider, final Cache<Long, ExchangeRateInfo> historicalRates) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.historicalRates = requireNonNull(historicalRates);
    }

    public void init(
            @NonNull final State state, @NonNull final Bytes bytes, @NonNull final ExchangeRateSet midnightRateSet) {
        this.state = requireNonNull(state);
        requireNonNull(bytes);
        requireNonNull(midnightRateSet);
        // Re-use the same code path to set the active rates as does the SystemFileUpdateFacility
        // IMPORTANT - if we first initialized the midnight rates, we couldn't reuse this code path
        // because it overwrites the midnight rates with the current rates
        systemUpdate(bytes);
        midnightRates = midnightRateSet;
        log.info(
                "Initializing exchange rates with midnight rates {} and active rates {}",
                midnightRates,
                currentExchangeRateInfo.exchangeRates());
    }

    /**
     * Updates the exchange rate information. MUST BE CALLED on the handle thread!
     *
     * @param bytes The protobuf encoded {@link ExchangeRateSet}.
     * @param payerId The payer of the transaction that triggered this update.
     */
    public void update(@NonNull final Bytes bytes, @NonNull AccountID payerId) {
        requireNonNull(payerId, "payerId must not be null");
        internalUpdate(bytes, payerId);
    }

    public void systemUpdate(@NonNull final Bytes bytes) {
        internalUpdate(bytes, null);
    }

    private void internalUpdate(@NonNull final Bytes bytes, @Nullable AccountID payerId) {
        requireNonNull(bytes, "bytes must not be null");

        final var proposedRates = parseExchangeRates(bytes);

        // Check bounds
        final var ratesConfig = configProvider.getConfiguration().getConfigData(RatesConfig.class);
        final var accountsConfig = configProvider.getConfiguration().getConfigData(AccountsConfig.class);
        final var isSuperUser = isSuperUser(payerId, accountsConfig);

        if (!isSuperUser) {
            final var limitPercent = ratesConfig.intradayChangeLimitPercent();
            if (!isNormalIntradayChange(midnightRates, proposedRates, limitPercent)) {
                throw new HandleException(ResponseCodeEnum.EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED);
            }
        }

        // Update the current ExchangeRateInfo and eventually the midnightRates
        this.currentExchangeRateInfo = new ExchangeRateInfoImpl(proposedRates);
        if (isAdminUser(payerId, accountsConfig)) {
            midnightRates = proposedRates;
        }
    }

    private static ExchangeRateSet parseExchangeRates(final Bytes bytes) {
        // Parse the exchange rate file. If we cannot parse it, we just continue with whatever our previous rate was.
        final ExchangeRateSet proposedRates;
        try {
            proposedRates = ExchangeRateSet.PROTOBUF.parseStrict(bytes.toReadableSequentialData());
        } catch (final ParseException e) {
            throw new HandleException(ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE);
        }

        // Validate mandatory fields
        if (!(proposedRates.hasCurrentRate()
                && proposedRates.currentRateOrThrow().hasExpirationTime()
                && proposedRates.hasNextRate())) {
            throw new HandleException(ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE);
        }

        // Reject degenerate rates: a non-positive hbarEquiv/centEquiv would make fee conversion divide by
        // zero (see getAFromB) or yield a nonsensical price, so such a rate must never become the live rate.
        final var current = proposedRates.currentRateOrThrow();
        final var next = proposedRates.nextRateOrThrow();
        if (current.hbarEquiv() <= 0 || current.centEquiv() <= 0 || next.hbarEquiv() <= 0 || next.centEquiv() <= 0) {
            throw new HandleException(ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE);
        }

        return proposedRates;
    }

    private boolean isSuperUser(@NonNull final AccountID accountID, AccountsConfig accountsConfig) {
        if (accountID == null) return true;
        if (!accountID.hasAccountNum()) return false;
        long num = accountID.accountNumOrThrow();
        return num == accountsConfig.treasury() || num == accountsConfig.systemAdmin();
    }

    private boolean isAdminUser(@NonNull final AccountID accountID, AccountsConfig accountsConfig) {
        if (accountID == null) return true;
        if (!accountID.hasAccountNum()) return false;
        long num = accountID.accountNumOrThrow();
        return num == accountsConfig.systemAdmin();
    }

    /**
     * Updates the midnight rates to the current exchange rates, both internally and in the given state.
     *
     * @param state the {@link State} to update the midnight rates in
     */
    public void updateMidnightRates(@NonNull final State state) {
        midnightRates = currentExchangeRateInfo.exchangeRates();
        final var singleton = state.getWritableStates(FeeService.NAME)
                .<ExchangeRateSet>getSingleton(V0490FeeSchema.MIDNIGHT_RATES_STATE_ID);
        singleton.put(midnightRates);
        log.info("Updated midnight rates to {}", midnightRates);
    }

    /**
     * Gets the {@link ExchangeRateSet} selected by the current execution context.
     *
     * @return The current {@link ExchangeRateSet}.
     */
    @NonNull
    public ExchangeRateSet exchangeRates() {
        return executionExchangeRateInfo().exchangeRates();
    }

    /**
     * Gets the {@link ExchangeRate} at the given consensus time from the rates selected by the execution context.
     *
     * @param consensusTime The consensus time. If after the expiration time of the current rate, the next rate will
     * be returned. Otherwise, the current rate will be returned.
     * @return The {@link ExchangeRate} that should be used as of the given consensus time.
     */
    @NonNull
    public ExchangeRate activeRate(@NonNull final Instant consensusTime) {
        return executionExchangeRateInfo().activeRate(consensusTime);
    }

    /**
     * Gets exchange rates from the given state, sharing cached historical rates across executors.
     *
     * @param state The {@link State} to use.
     * @return The {@link ExchangeRateInfo}.
     */
    @NonNull
    public ExchangeRateInfo exchangeRateInfo(@NonNull final State state) {
        if (ContractCallContext.isInitialized()) {
            final var timestamp = ContractCallContext.get().getTimestampForSystemFiles();
            if (timestamp.isPresent()) {
                return historicalExchangeRateInfo(state, timestamp.get());
            }
        }
        final var bytes = exchangeRateFileContents(state);
        final ExchangeRateSet exchangeRates;
        try {
            exchangeRates = ExchangeRateSet.PROTOBUF.parseStrict(bytes.toReadableSequentialData());
        } catch (ParseException e) {
            // This should never happen
            throw new IllegalStateException(e);
        }
        return new ExchangeRateInfoImpl(exchangeRates);
    }

    private ExchangeRateInfo executionExchangeRateInfo() {
        if (state != null && ContractCallContext.isInitialized()) {
            final var timestamp = ContractCallContext.get().getTimestampForSystemFiles();
            if (timestamp.isPresent()) {
                return historicalExchangeRateInfo(state, timestamp.get());
            }
        }
        return currentExchangeRateInfo;
    }

    private ExchangeRateInfo historicalExchangeRateInfo(final State state, final long timestamp) {
        final Long hour = timestamp / NANOS_PER_HOUR * NANOS_PER_HOUR;
        // Avoid allocating a capturing loader on cache hits. Caffeine coalesces concurrent loads of the same hour.
        var info = historicalRates.getIfPresent(hour);
        if (info == null) {
            info = historicalRates.get(hour, ignored -> loadHistoricalExchangeRateInfo(state));
        }
        // Never cache the latest rates as a substitute for a missing or invalid historical file.
        return info != null ? info : currentExchangeRateInfo;
    }

    private @Nullable ExchangeRateInfo loadHistoricalExchangeRateInfo(final State state) {
        final var bytes = exchangeRateFileContents(state);
        try {
            return new ExchangeRateInfoImpl(parseExchangeRates(bytes));
        } catch (final HandleException e) {
            log.warn("Ignoring invalid historical exchange rates: {}", e.getStatus());
            return null;
        }
    }

    private Bytes exchangeRateFileContents(final State state) {
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        final var shardNum = hederaConfig.shard();
        final var realmNum = hederaConfig.realm();
        final var fileNum = configProvider
                .getConfiguration()
                .getConfigData(FilesConfig.class)
                .exchangeRates();
        final var fileID = FileID.newBuilder()
                .shardNum(shardNum)
                .realmNum(realmNum)
                .fileNum(fileNum)
                .build();
        return FileUtilities.getFileContent(state, fileID);
    }

    private static boolean isNormalIntradayChange(
            @NonNull final ExchangeRateSet midnightRates,
            @NonNull final ExchangeRateSet proposedRates,
            final int limitPercent) {
        return canonicalTest(
                        limitPercent,
                        midnightRates.currentRate().centEquiv(),
                        midnightRates.currentRate().hbarEquiv(),
                        proposedRates.currentRate().centEquiv(),
                        proposedRates.currentRate().hbarEquiv())
                && canonicalTest(
                        limitPercent,
                        midnightRates.nextRate().centEquiv(),
                        midnightRates.nextRate().hbarEquiv(),
                        proposedRates.nextRate().centEquiv(),
                        proposedRates.nextRate().hbarEquiv());
    }

    private static boolean canonicalTest(
            final long bound, final long oldC, final long oldH, final long newC, final long newH) {
        final var b100 = valueOf(bound).add(ONE_HUNDRED);

        final var oC = valueOf(oldC);
        final var oH = valueOf(oldH);
        final var nC = valueOf(newC);
        final var nH = valueOf(newH);

        return LongStream.of(bound, oldC, oldH, newC, newH).allMatch(i -> i > 0)
                && oC.multiply(nH)
                                .multiply(b100)
                                .subtract(nC.multiply(oH).multiply(ONE_HUNDRED))
                                .signum()
                        >= 0
                && oH.multiply(nC)
                                .multiply(b100)
                                .subtract(nH.multiply(oC).multiply(ONE_HUNDRED))
                                .signum()
                        >= 0;
    }

    /**
     * Converts tinybars to tiny cents using the exchange rate at a given time.
     *
     * @param amount The amount in tiny cents.
     * @param consensusTime The consensus time to use for the exchange rate.
     * @return The amount in tinybars.
     */
    public long getTinybarsFromTinycents(final long amount, @NonNull final Instant consensusTime) {
        final var rate = activeRate(consensusTime);
        return getAFromB(amount, rate.hbarEquiv(), rate.centEquiv());
    }

    private static long getAFromB(final long bAmount, final int aEquiv, final int bEquiv) {
        // A degenerate exchange rate would either divide by zero (non-positive divisor) or make the result
        // free/negative (non-positive multiplier); saturate to Long.MAX_VALUE instead so a malformed rate
        // yields an unpayable fee and cannot halt fee conversion network-wide.
        if (aEquiv <= 0 || bEquiv <= 0) {
            return Long.MAX_VALUE;
        }
        final var result =
                BigInteger.valueOf(bAmount).multiply(BigInteger.valueOf(aEquiv)).divide(BigInteger.valueOf(bEquiv));
        // longValueExact() throws when the result exceeds the long range; saturate to the maximum instead.
        return result.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : result.longValue();
    }
}
