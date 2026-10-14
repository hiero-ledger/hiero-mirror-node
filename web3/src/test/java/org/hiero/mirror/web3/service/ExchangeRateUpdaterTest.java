// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.state.SystemFileLoader;
import org.hiero.mirror.web3.state.Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRateUpdaterTest {

    private static final Instant HOUR_10 = Instant.parse("2026-10-14T10:00:00Z");
    private static final Instant HOUR_11 = HOUR_10.plus(Duration.ofHours(1));
    private static final int HBAR_EQUIV = 30_000;

    private final EvmProperties evmProperties = new EvmProperties();
    private final SystemEntity systemEntity = new SystemEntity(CommonProperties.getInstance());
    private final FileID exchangeRateFileId = Utils.toFileID(systemEntity.exchangeRateFile());
    private final Web3Properties web3Properties = new Web3Properties();

    @Mock
    private SystemFileLoader systemFileLoader;

    @Mock
    private TransactionExecutorFactory transactionExecutorFactory;

    private ExchangeRateManager exchangeRateManager;
    private ExchangeRateUpdater exchangeRateUpdater;

    @BeforeEach
    void setup() {
        exchangeRateManager = newExchangeRateManager(rates(100_000, HOUR_10));
        exchangeRateUpdater =
                new ExchangeRateUpdater(systemEntity, systemFileLoader, transactionExecutorFactory, web3Properties);
    }

    @AfterEach
    void teardown() {
        exchangeRateUpdater.stop();
    }

    @Test
    void firstRunIsTwoMinutesAfterTheHour() {
        assertThat(exchangeRateUpdater.delayUntilNextRun(HOUR_10)).isEqualTo(Duration.ofMinutes(2));
        assertThat(exchangeRateUpdater.delayUntilNextRun(HOUR_10.plusSeconds(30)))
                .isEqualTo(Duration.ofMinutes(2).minusSeconds(30));
        assertThat(exchangeRateUpdater.delayUntilNextRun(HOUR_10.plus(Duration.ofMinutes(2))))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void runAfterTheOffsetWaitsForTheNextHour() {
        assertThat(exchangeRateUpdater.delayUntilNextRun(
                        HOUR_10.plus(Duration.ofMinutes(2)).plusMillis(1)))
                .isEqualTo(Duration.ofHours(1).minusMillis(1));
        assertThat(exchangeRateUpdater.delayUntilNextRun(HOUR_10.plus(Duration.ofMinutes(30))))
                .isEqualTo(Duration.ofMinutes(32));
        assertThat(exchangeRateUpdater.delayUntilNextRun(HOUR_11.minusMillis(1)))
                .isEqualTo(Duration.ofMinutes(2).plusMillis(1));
    }

    @Test
    void firstRunUsesConfiguredOffsetAndPeriod() {
        web3Properties.setExchangeRateRefreshOffset(Duration.ofMinutes(10));
        web3Properties.setExchangeRateRefreshPeriod(Duration.ofMinutes(30));

        assertThat(exchangeRateUpdater.delayUntilNextRun(HOUR_10)).isEqualTo(Duration.ofMinutes(10));
        assertThat(exchangeRateUpdater.delayUntilNextRun(HOUR_10.plus(Duration.ofMinutes(15))))
                .isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    void refreshUpdatesEveryExchangeRateManager() {
        final var newRates = rates(120_000, HOUR_11);
        final var otherManager = newExchangeRateManager(rates(90_000, HOUR_10));
        when(transactionExecutorFactory.getExchangeRateManagers())
                .thenReturn(List.of(exchangeRateManager, otherManager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(file(newRates));

        exchangeRateUpdater.refresh(HOUR_10);

        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(newRates);
        assertThat(otherManager.exchangeRates()).isEqualTo(newRates);
    }

    @Test
    void skipsUpdateWhenRatesAreUnchanged() {
        final var unchangedRates = rates(120_000, HOUR_11);
        final var manager = spy(newExchangeRateManager(unchangedRates));
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(manager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(file(unchangedRates));

        exchangeRateUpdater.refresh(HOUR_11);

        verify(manager, never()).systemUpdate(any());
    }

    @Test
    void ignoresInvalidRates() {
        final var initialRates = exchangeRateManager.exchangeRates();
        final var degenerateRates = rates(120_000, HOUR_11)
                .copyBuilder()
                .currentRate(rate(120_000, HOUR_11).copyBuilder().hbarEquiv(0).build())
                .build();
        final var manager = spy(exchangeRateManager);
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(manager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(file(degenerateRates));

        exchangeRateUpdater.refresh(HOUR_10);

        verify(manager, times(1)).systemUpdate(any());
        assertThat(manager.exchangeRates()).isEqualTo(initialRates);
    }

    @Test
    void appliesALaterValidFileAfterInvalidRates() {
        final var validRates = rates(120_000, HOUR_11);
        final var degenerateRates = rates(120_000, HOUR_11)
                .copyBuilder()
                .currentRate(rate(120_000, HOUR_11).copyBuilder().hbarEquiv(0).build())
                .build();
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong()))
                .thenReturn(file(degenerateRates))
                .thenReturn(file(validRates));

        exchangeRateUpdater.refresh(HOUR_10);
        exchangeRateUpdater.refresh(HOUR_11);

        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(validRates);
    }

    @Test
    void missingFileLeavesRatesUnchanged() {
        final var initialRates = exchangeRateManager.exchangeRates();
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(null);

        exchangeRateUpdater.refresh(HOUR_10);

        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(initialRates);
    }

    @Test
    void ignoresUnparseableFile() {
        final var initialRates = exchangeRateManager.exchangeRates();
        final var garbage = File.newBuilder()
                .fileId(exchangeRateFileId)
                .contents(Bytes.wrap(new byte[] {1}))
                .build();
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(garbage);

        exchangeRateUpdater.refresh(HOUR_10);

        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(initialRates);
    }

    @Test
    void scheduledRunsNeverOverlap() {
        final var newRates = rates(120_000, HOUR_11);
        final var runs = new AtomicInteger();
        final var active = new AtomicInteger();
        final var maxActive = new AtomicInteger();
        final var threads = ConcurrentHashMap.<String>newKeySet();
        when(transactionExecutorFactory.getExchangeRateManagers()).thenAnswer(invocation -> {
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            threads.add(Thread.currentThread().getName());
            // Each run outlasts the period, so the next one is already due when it finishes
            Thread.sleep(20);
            active.decrementAndGet();
            runs.incrementAndGet();
            return List.of(exchangeRateManager);
        });
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(file(newRates));

        exchangeRateUpdater.start(Duration.ZERO, Duration.ofMillis(1));

        await().atMost(Duration.ofSeconds(5)).until(() -> runs.get() >= 5);
        assertThat(maxActive).hasValue(1);
        assertThat(threads).containsExactly("exchange-rate-updater");
        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(newRates);
    }

    @Test
    void scheduledRunsContinueAfterFailure() {
        final var newRates = rates(120_000, HOUR_11);
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong()))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(file(newRates));

        exchangeRateUpdater.start(Duration.ZERO, Duration.ofMillis(10));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () -> assertThat(exchangeRateManager.exchangeRates()).isEqualTo(newRates));
    }

    @Test
    void startOnlySchedulesOnce() {
        exchangeRateUpdater.start(Duration.ZERO, Duration.ofHours(1));
        verify(systemFileLoader, timeout(5_000)).load(eq(exchangeRateFileId), anyLong());

        exchangeRateUpdater.start(Duration.ZERO, Duration.ofMillis(1));
        verify(systemFileLoader, after(200).times(1)).load(eq(exchangeRateFileId), anyLong());
    }

    private ExchangeRateManager newExchangeRateManager(final ExchangeRateSet initialRates) {
        final var configProvider = new ConfigProviderImpl(false, null, evmProperties.getTransactionProperties());
        final var manager = new ExchangeRateManager(configProvider);
        manager.systemUpdate(ExchangeRateSet.PROTOBUF.toBytes(initialRates));
        return manager;
    }

    private File file(final ExchangeRateSet exchangeRates) {
        return File.newBuilder()
                .fileId(exchangeRateFileId)
                .contents(ExchangeRateSet.PROTOBUF.toBytes(exchangeRates))
                .build();
    }

    private static ExchangeRateSet rates(final int centEquiv, final Instant expiry) {
        return ExchangeRateSet.newBuilder()
                .currentRate(rate(centEquiv, expiry))
                .nextRate(rate(centEquiv, expiry.plus(Duration.ofHours(1))))
                .build();
    }

    private static ExchangeRate rate(final int centEquiv, final Instant expiry) {
        return ExchangeRate.newBuilder()
                .hbarEquiv(HBAR_EQUIV)
                .centEquiv(centEquiv)
                .expirationTime(new TimestampSeconds(expiry.getEpochSecond()))
                .build();
    }
}
