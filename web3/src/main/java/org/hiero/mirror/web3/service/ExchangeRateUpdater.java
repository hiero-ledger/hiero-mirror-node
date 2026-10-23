// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.state.SystemFileLoader;
import org.hiero.mirror.web3.state.Utils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Reloads the exchange rate file into every transaction executor's in-memory {@link ExchangeRateManager}. The consensus
 * node refreshes those rates when it handles a FileUpdate of the exchange rate file, which never happens in the mirror
 * node, so without this the rates would stay at the values read when each executor was created.
 *
 * <p>The check runs every {@code hiero.mirror.web3.exchangeRateRefreshPeriod}. Only the current rate's expiration is
 * remembered. While that expiration is still in the future the database is left alone. Once it has passed, the file is
 * loaded through {@link SystemFileLoader} and applied to each manager whose rates differ.
 *
 * <p>A historical call loads the exchange rate file at the block timestamp for the duration of that call, then restores
 * the rates that were in memory. That load does not change the expiration remembered for the scheduled refresh.
 */
@CustomLog
@Named
@NullMarked
final class ExchangeRateUpdater {

    private final AtomicLong expirationTime = new AtomicLong();
    private final FileID exchangeRateFileId;
    private final SystemFileLoader systemFileLoader;
    private final TransactionExecutorFactory transactionExecutorFactory;

    public ExchangeRateUpdater(
            final SystemEntity systemEntity,
            final SystemFileLoader systemFileLoader,
            final TransactionExecutorFactory transactionExecutorFactory) {
        this.exchangeRateFileId = Utils.toFileID(systemEntity.exchangeRateFile());
        this.systemFileLoader = systemFileLoader;
        this.transactionExecutorFactory = transactionExecutorFactory;
    }

    @Scheduled(fixedDelayString = "${hiero.mirror.web3.exchangeRateRefreshInterval:PT05S}", initialDelay = 10)
    public synchronized void refresh() {
        try {
            final var now = Instant.now();
            if (expirationTime.get() < now.getEpochSecond()) {
                refresh(now);
            }
        } catch (final Exception e) {
            log.warn("Unable to refresh exchange rates: {}", e.getMessage());
        }
    }

    /**
     * Runs {@code call} with the exchange rate file at {@code consensusNanos} applied to every manager, then restores
     * the previous rates. The scheduled refresh expiration is left unchanged.
     */
    synchronized <T> T callAt(final long consensusNanos, final Supplier<T> call) {
        final var loaded = load(consensusNanos);
        if (loaded == null) {
            return call.get();
        }

        final var exchangeRates = loaded.rates();
        if (!exchangeRates.hasCurrentRate()
                || !exchangeRates.currentRateOrThrow().hasExpirationTime()) {
            log.warn("Ignoring exchange rates without an expiration time: {}", exchangeRates);
            return call.get();
        }

        final var managers = transactionExecutorFactory.getExchangeRateManagers();
        final var previous = new Bytes[managers.size()];
        for (var i = 0; i < managers.size(); i++) {
            previous[i] = ExchangeRateSet.PROTOBUF.toBytes(managers.get(i).exchangeRates());
        }
        for (final var exchangeRateManager : managers) {
            apply(exchangeRateManager, loaded.contents(), exchangeRates);
        }

        try {
            return call.get();
        } finally {
            for (var i = 0; i < managers.size(); i++) {
                restore(managers.get(i), previous[i]);
            }
        }
    }

    /** Applies the exchange rate file at {@code now}. The scheduled check calls this once the current rate has expired. */
    synchronized void refresh(final Instant now) {
        final var loaded = load(DomainUtils.convertToNanos(now.getEpochSecond(), now.getNano()));
        if (loaded == null) {
            return;
        }

        final var exchangeRates = loaded.rates();
        if (!exchangeRates.hasCurrentRate()
                || !exchangeRates.currentRateOrThrow().hasExpirationTime()) {
            log.warn("Ignoring exchange rates without an expiration time: {}", exchangeRates);
            return;
        }

        var updated = 0;
        for (final var exchangeRateManager : transactionExecutorFactory.getExchangeRateManagers()) {
            final var result = apply(exchangeRateManager, loaded.contents(), exchangeRates);
            if (result == UpdateResult.UPDATED) {
                ++updated;
            }
        }

        expirationTime.set(
                exchangeRates.currentRateOrThrow().expirationTimeOrThrow().seconds());
        if (updated > 0) {
            log.info(
                    "Updated {} exchange rate managers from overall {}.",
                    updated,
                    transactionExecutorFactory.getExchangeRateManagers().size());
        }
    }

    private UpdateResult apply(
            final ExchangeRateManager exchangeRateManager, final Bytes contents, final ExchangeRateSet exchangeRates) {
        try {
            if (exchangeRates.equals(exchangeRateManager.exchangeRates())) {
                return UpdateResult.UNCHANGED;
            }

            exchangeRateManager.systemUpdate(contents);
            return UpdateResult.UPDATED;
        } catch (final HandleException e) {
            log.warn("Ignoring invalid exchange rates: {}", e.getStatus());
            return UpdateResult.FAILED;
        }
    }

    private @Nullable LoadedExchangeRates load(final long consensusNanos) {
        final var file = systemFileLoader.load(exchangeRateFileId, consensusNanos);
        if (file == null) {
            return null;
        }

        try {
            return new LoadedExchangeRates(file.contents(), ExchangeRateSet.PROTOBUF.parse(file.contents()));
        } catch (final ParseException e) {
            log.warn("Unable to parse exchange rates: {}", e.getMessage());
            return null;
        }
    }

    private void restore(final ExchangeRateManager exchangeRateManager, final Bytes contents) {
        try {
            exchangeRateManager.systemUpdate(contents);
        } catch (final HandleException e) {
            log.warn("Unable to restore exchange rates: {}", e.getStatus());
        }
    }

    private record LoadedExchangeRates(Bytes contents, ExchangeRateSet rates) {}

    private enum UpdateResult {
        FAILED,
        UNCHANGED,
        UPDATED
    }
}
