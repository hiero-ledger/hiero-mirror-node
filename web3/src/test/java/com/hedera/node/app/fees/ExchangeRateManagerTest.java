// SPDX-License-Identifier: Apache-2.0

package com.hedera.node.app.fees;

import static com.hedera.node.app.service.file.impl.schemas.V0490FileSchema.FILES_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ExchangeRateManagerTest {

    private static final long NANOS_PER_HOUR = Duration.ofHours(1).toNanos();
    private static final long HOUR = Instant.parse("2026-10-14T10:00:00Z").getEpochSecond() * 1_000_000_000L;
    private static final ExchangeRateSet LATEST_RATES = rates(300);
    private static final ExchangeRateSet HISTORICAL_RATES = rates(100);

    private final State state = mock(State.class);
    private final ReadableStates states = mock(ReadableStates.class);
    private final ReadableKVState<FileID, File> files = mock();
    private final AtomicLong ticker = new AtomicLong();
    private final ConfigProviderImpl configProvider =
            new ConfigProviderImpl(false, null, new EvmProperties().getTransactionProperties());

    private Cache<Long, ExchangeRateInfo> cache;
    private ExchangeRateManager manager;

    @BeforeEach
    void setup() {
        ExchangeRateManagerTestSupport.clearHistoricalRates();
        cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .ticker(ticker::get)
                .build();
        manager = new ExchangeRateManager(configProvider, cache);
        manager.init(state, ExchangeRateSet.PROTOBUF.toBytes(LATEST_RATES), LATEST_RATES);
        when(state.getReadableStates(FileService.NAME)).thenReturn(states);
        when(states.<FileID, File>get(FILES_STATE_ID)).thenReturn(files);
    }

    @AfterEach
    void cleanup() {
        ExchangeRateManagerTestSupport.clearHistoricalRates();
    }

    @Test
    void latestReadsUsePublishedRatesWithoutHistoricalLookup() {
        assertThat(manager.exchangeRates()).isEqualTo(LATEST_RATES);
        ContractCallContext.run(ctx -> {
            ctx.setTimestamp(Optional.of(HOUR));
            ctx.setCallServiceParameters(ContractExecutionParameters.builder()
                    .block(BlockType.LATEST)
                    .build());
            assertThat(manager.exchangeRates()).isEqualTo(LATEST_RATES);
            assertThat(manager.activeRate(Instant.EPOCH)).isEqualTo(LATEST_RATES.currentRate());
            return null;
        });
        verify(files, never()).get(any());
    }

    @Test
    void allHistoricalAccessorsUseOneParsedRate() {
        when(files.get(any())).thenReturn(file(HISTORICAL_RATES));

        historical(HOUR, () -> {
            assertThat(manager.exchangeRates()).isEqualTo(HISTORICAL_RATES);
            assertThat(manager.activeRate(Instant.EPOCH)).isEqualTo(HISTORICAL_RATES.currentRate());
            assertThat(manager.getTinybarsFromTinycents(100, Instant.EPOCH)).isEqualTo(30_000);
            assertThat(manager.exchangeRateInfo(state)).isSameAs(cache.getIfPresent(HOUR));
            return null;
        });

        verify(files).get(any());
        assertThat(manager.exchangeRates()).isEqualTo(LATEST_RATES);
    }

    @Test
    void historicalActiveRateChangesAtCurrentRateExpiration() {
        when(files.get(any())).thenReturn(file(HISTORICAL_RATES));
        final var expiry = Instant.ofEpochSecond(
                HISTORICAL_RATES.currentRateOrThrow().expirationTimeOrThrow().seconds());

        historical(HOUR, () -> {
            assertThat(manager.activeRate(expiry.minusNanos(1))).isEqualTo(HISTORICAL_RATES.currentRate());
            assertThat(manager.activeRate(expiry)).isEqualTo(HISTORICAL_RATES.nextRate());
            return null;
        });
    }

    @Test
    void updateRetainsUpstreamIntradayChangeValidation() {
        final var payer = AccountID.newBuilder().accountNum(1234).build();

        assertThatThrownBy(() -> manager.update(ExchangeRateSet.PROTOBUF.toBytes(HISTORICAL_RATES), payer))
                .isInstanceOfSatisfying(
                        HandleException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(ResponseCodeEnum.EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED));
        assertThat(manager.exchangeRates()).isEqualTo(LATEST_RATES);
    }

    @Test
    void defaultManagersShareRatesForTimestampsInTheSameHour() {
        final var first = new ExchangeRateManager(configProvider);
        final var second = new ExchangeRateManager(configProvider);
        first.init(state, ExchangeRateSet.PROTOBUF.toBytes(LATEST_RATES), LATEST_RATES);
        second.init(state, ExchangeRateSet.PROTOBUF.toBytes(LATEST_RATES), LATEST_RATES);
        when(files.get(any())).thenReturn(file(HISTORICAL_RATES));

        final var info = historical(HOUR, () -> first.exchangeRateInfo(state));
        assertThat(historical(HOUR + 1, () -> second.exchangeRateInfo(state))).isSameAs(info);

        verify(files).get(any());
        final var globalCache = ExchangeRateManager.HISTORICAL_RATES;
        assertThat(globalCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(globalCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(1000);
    }

    @Test
    void hourBoundaryUsesTheAdjustedSystemFileTimestamp() {
        when(files.get(any())).thenReturn(file(HISTORICAL_RATES), file(LATEST_RATES));

        assertThat(historical(HOUR - 1, manager::exchangeRates)).isEqualTo(HISTORICAL_RATES);
        assertThat(historical(HOUR, manager::exchangeRates)).isEqualTo(LATEST_RATES);

        assertThat(cache.asMap()).containsOnlyKeys(HOUR - NANOS_PER_HOUR, HOUR);
        verify(files, times(2)).get(any());
    }

    @Test
    void opcodeReplayUsesItsSystemFileTimestampEvenWithLatestBlock() {
        when(files.get(any())).thenReturn(file(HISTORICAL_RATES));

        ContractCallContext.run(ctx -> {
            ctx.setTimestamp(Optional.of(HOUR - 1));
            ctx.setCallServiceParameters(ContractExecutionParameters.builder()
                    .block(BlockType.LATEST)
                    .build());
            ctx.setOpcodeContext(new OpcodeContext(
                    new OpcodeRequest(new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH), false, false, false),
                    0,
                    new OpcodesProperties()));
            assertThat(manager.exchangeRates()).isEqualTo(HISTORICAL_RATES);
            assertThat(cache.asMap()).containsOnlyKeys(HOUR);
            return null;
        });
    }

    @Test
    void recentHistoricalTimestampAlsoUsesHistoricalRates() {
        when(files.get(any())).thenReturn(file(HISTORICAL_RATES));
        final long recentTimestamp = Instant.now().minusSeconds(60).getEpochSecond() * 1_000_000_000L;

        assertThat(historical(recentTimestamp, manager::exchangeRates)).isEqualTo(HISTORICAL_RATES);
    }

    @Test
    void cacheExpiresFiveMinutesAfterWriteDespiteReads() {
        when(files.get(any())).thenReturn(file(HISTORICAL_RATES), file(LATEST_RATES));
        assertThat(historical(HOUR, manager::exchangeRates)).isEqualTo(HISTORICAL_RATES);

        ticker.addAndGet(Duration.ofMinutes(5).minusNanos(1).toNanos());
        assertThat(historical(HOUR, manager::exchangeRates)).isEqualTo(HISTORICAL_RATES);
        ticker.incrementAndGet();
        assertThat(historical(HOUR, manager::exchangeRates)).isEqualTo(LATEST_RATES);

        verify(files, times(2)).get(any());
    }

    @Test
    void cacheIsBoundedToOneThousandHours() {
        when(files.get(any())).thenReturn(file(HISTORICAL_RATES));
        for (var hour = 0; hour < 1001; ++hour) {
            historical(HOUR + hour * NANOS_PER_HOUR, manager::exchangeRates);
        }
        cache.cleanUp();

        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(1000);
    }

    @ParameterizedTest
    @MethodSource("invalidFiles")
    void invalidHistoricalFilesFallBackWithoutCachingAndCanBeRetried(final Bytes contents) {
        when(files.get(any())).thenReturn(File.newBuilder().contents(contents).build(), file(HISTORICAL_RATES));

        assertThat(historical(HOUR, manager::exchangeRates)).isEqualTo(LATEST_RATES);
        assertThat(cache.estimatedSize()).isZero();
        assertThat(historical(HOUR, manager::exchangeRates)).isEqualTo(HISTORICAL_RATES);
        verify(files, times(2)).get(any());
    }

    @Test
    void missingHistoricalFileFallsBackWithoutCachingAndCanBeRetried() {
        when(files.get(any())).thenReturn(null, file(HISTORICAL_RATES));

        assertThat(historical(HOUR, () -> manager.exchangeRateInfo(state)).exchangeRates())
                .isEqualTo(LATEST_RATES);
        assertThat(cache.estimatedSize()).isZero();
        assertThat(historical(HOUR, () -> manager.exchangeRateInfo(state)).exchangeRates())
                .isEqualTo(HISTORICAL_RATES);
    }

    @Test
    void unexpectedLoadFailuresRemainVisible() {
        final var failure = new IllegalStateException("database unavailable");
        when(files.get(any())).thenThrow(failure);

        assertThatThrownBy(() -> historical(HOUR, manager::exchangeRates)).isSameAs(failure);
        assertThat(cache.estimatedSize()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidFiles")
    void systemUpdateStillRejectsInvalidRates(final Bytes contents) {
        assertThatThrownBy(() -> manager.systemUpdate(contents)).isInstanceOf(HandleException.class);
        assertThat(manager.exchangeRates()).isEqualTo(LATEST_RATES);
    }

    @Test
    void latestStateLookupRetainsItsExistingBehavior() {
        when(files.get(any())).thenReturn(file(HISTORICAL_RATES));

        assertThat(manager.exchangeRateInfo(state).exchangeRates()).isEqualTo(HISTORICAL_RATES);
        assertThat(manager.exchangeRates()).isEqualTo(LATEST_RATES);
        assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    void concurrentMissesForTheSameHourShareOneLoad() throws Exception {
        final var ready = new CountDownLatch(2);
        final var start = new CountDownLatch(1);
        final var loading = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        when(files.get(any())).thenAnswer(ignored -> {
            loading.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return file(HISTORICAL_RATES);
        });

        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return historical(HOUR, () -> manager.exchangeRateInfo(state));
            });
            final var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return historical(HOUR + 1, () -> manager.exchangeRateInfo(state));
            });
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
                release.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(second.get(5, TimeUnit.SECONDS));
        }
        verify(files).get(any());
    }

    @Test
    void differentHistoricalCallsRemainIsolatedDuringLatestRefresh() throws Exception {
        final var otherHistoricalRates = rates(200);
        final var refreshedRates = rates(400);
        final var ready = new CountDownLatch(2);
        final var refreshed = new CountDownLatch(1);
        when(files.get(any()))
                .thenAnswer(ignored -> file(
                        ContractCallContext.get().getTimestampForSystemFiles().orElseThrow() < HOUR
                                ? HISTORICAL_RATES
                                : otherHistoricalRates));

        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var first = executor.submit(() -> historical(HOUR - 1, () -> {
                final var before = manager.exchangeRates();
                ready.countDown();
                await(refreshed);
                assertThat(manager.exchangeRates()).isSameAs(before);
                return before;
            }));
            final var second = executor.submit(() -> historical(HOUR, () -> {
                final var before = manager.exchangeRates();
                ready.countDown();
                await(refreshed);
                assertThat(manager.exchangeRates()).isSameAs(before);
                return before;
            }));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                manager.systemUpdate(ExchangeRateSet.PROTOBUF.toBytes(refreshedRates));
            } finally {
                refreshed.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(HISTORICAL_RATES);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(otherHistoricalRates);
        }
        assertThat(manager.exchangeRates()).isEqualTo(refreshedRates);
        verify(files, times(2)).get(any());
    }

    private static void await(final CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static <T> T historical(final long systemFileTimestamp, final Supplier<T> action) {
        return ContractCallContext.run(ctx -> {
            ctx.setTimestamp(Optional.of(systemFileTimestamp - 1));
            ctx.setCallServiceParameters(ContractExecutionParameters.builder()
                    .block(BlockType.EARLIEST)
                    .build());
            return action.get();
        });
    }

    private static File file(final ExchangeRateSet rates) {
        return File.newBuilder()
                .contents(ExchangeRateSet.PROTOBUF.toBytes(rates))
                .build();
    }

    private static ExchangeRateSet rates(final int cents) {
        final var current = ExchangeRate.newBuilder()
                .hbarEquiv(30_000)
                .centEquiv(cents)
                .expirationTime(new TimestampSeconds(HOUR / 1_000_000_000L + 3600))
                .build();
        return ExchangeRateSet.newBuilder()
                .currentRate(current)
                .nextRate(current.copyBuilder().centEquiv(cents * 2).build())
                .build();
    }

    private static Stream<Bytes> invalidFiles() {
        return Stream.concat(
                Stream.of(
                                ExchangeRateSet.DEFAULT,
                                HISTORICAL_RATES
                                        .copyBuilder()
                                        .nextRate((ExchangeRate) null)
                                        .build(),
                                HISTORICAL_RATES
                                        .copyBuilder()
                                        .currentRate(HISTORICAL_RATES
                                                .currentRateOrThrow()
                                                .copyBuilder()
                                                .expirationTime((TimestampSeconds) null)
                                                .build())
                                        .build(),
                                HISTORICAL_RATES
                                        .copyBuilder()
                                        .currentRate(HISTORICAL_RATES
                                                .currentRateOrThrow()
                                                .copyBuilder()
                                                .hbarEquiv(0)
                                                .build())
                                        .build(),
                                HISTORICAL_RATES
                                        .copyBuilder()
                                        .currentRate(HISTORICAL_RATES
                                                .currentRateOrThrow()
                                                .copyBuilder()
                                                .centEquiv(-1)
                                                .build())
                                        .build(),
                                HISTORICAL_RATES
                                        .copyBuilder()
                                        .nextRate(HISTORICAL_RATES
                                                .nextRateOrThrow()
                                                .copyBuilder()
                                                .hbarEquiv(-1)
                                                .build())
                                        .build(),
                                HISTORICAL_RATES
                                        .copyBuilder()
                                        .nextRate(HISTORICAL_RATES
                                                .nextRateOrThrow()
                                                .copyBuilder()
                                                .centEquiv(0)
                                                .build())
                                        .build())
                        .map(ExchangeRateSet.PROTOBUF::toBytes),
                Stream.of(Bytes.wrap(new byte[] {1})));
    }
}
