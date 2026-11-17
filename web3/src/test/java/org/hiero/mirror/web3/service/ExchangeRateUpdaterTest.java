// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.state.SystemFileLoader;
import org.hiero.mirror.web3.state.Utils;
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

    @Mock
    private SystemFileLoader systemFileLoader;

    @Mock
    private TransactionExecutorFactory transactionExecutorFactory;

    private ExchangeRateManager exchangeRateManager;
    private ExchangeRateUpdater exchangeRateUpdater;

    @BeforeEach
    void setup() {
        exchangeRateManager = newExchangeRateManager(rates(100_000, HOUR_10));
        exchangeRateUpdater = new ExchangeRateUpdater(systemEntity, systemFileLoader, transactionExecutorFactory);
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
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));
        final var initialRates = exchangeRateManager.exchangeRates();
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(null);

        exchangeRateUpdater.refresh(HOUR_10);

        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(initialRates);
    }

    @Test
    void ignoresUnparseableFile() {
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));
        final var initialRates = exchangeRateManager.exchangeRates();
        final var garbage = File.newBuilder()
                .fileId(exchangeRateFileId)
                .contents(Bytes.wrap(new byte[] {1}))
                .build();
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(garbage);

        exchangeRateUpdater.refresh(HOUR_10);

        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(initialRates);
    }

    @Test
    void scheduledRefreshSkipsDatabaseWhileCurrentRateIsActive() {
        final var activeRates = rates(120_000, Instant.now().plus(Duration.ofHours(1)));
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(file(activeRates));

        exchangeRateUpdater.refresh(Instant.now());
        exchangeRateUpdater.refresh();

        verify(systemFileLoader).load(eq(exchangeRateFileId), anyLong());
        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(activeRates);
    }

    @Test
    void scheduledRefreshLoadsAgainAfterExpiration() {
        final var expiredRates = rates(120_000, Instant.now().minus(Duration.ofMinutes(1)));
        final var renewedRates = rates(130_000, Instant.now().plus(Duration.ofHours(1)));
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong()))
                .thenReturn(file(expiredRates))
                .thenReturn(file(renewedRates));

        exchangeRateUpdater.refresh();
        exchangeRateUpdater.refresh();

        verify(systemFileLoader, times(2)).load(eq(exchangeRateFileId), anyLong());
        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(renewedRates);
    }

    @Test
    void scheduledRefreshContinuesAfterFailure() {
        final var newRates = rates(120_000, Instant.now().minus(Duration.ofMinutes(1)));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong()))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(file(newRates));
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));

        exchangeRateUpdater.refresh();
        exchangeRateUpdater.refresh();

        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(newRates);
    }

    @Test
    void scheduledRefreshRetriesInvalidRatesWithFutureExpiration() {
        final var expiry = Instant.now().plus(Duration.ofHours(1));
        final var validRates = rates(120_000, expiry);
        final var invalidRates = validRates
                .copyBuilder()
                .nextRate(rate(120_000, expiry).copyBuilder().centEquiv(0).build())
                .build();
        when(transactionExecutorFactory.getExchangeRateManagers()).thenReturn(List.of(exchangeRateManager));
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(file(invalidRates), file(validRates));

        exchangeRateUpdater.refresh();
        exchangeRateUpdater.refresh();

        verify(systemFileLoader, times(2)).load(eq(exchangeRateFileId), anyLong());
        assertThat(exchangeRateManager.exchangeRates()).isEqualTo(validRates);
    }

    @Test
    void skipsDatabaseWhenNoExecutorsExist() {
        exchangeRateUpdater.refresh();

        verify(systemFileLoader, never()).load(any(), anyLong());
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
