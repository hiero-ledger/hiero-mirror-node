// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.state.SystemFileLoader;
import org.hiero.mirror.web3.state.Utils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Reloads the exchange rate file into every transaction executor's in-memory {@link ExchangeRateManager}. The consensus
 * node refreshes those rates when it handles a FileUpdate of the exchange rate file, which never happens in the mirror
 * node, so without this the rates would stay at the values read when each executor was created.
 *
 * <p>A single thread runs the reload every {@link Web3Properties#getExchangeRateRefreshPeriod()}, starting
 * {@link Web3Properties#getExchangeRateRefreshOffset()} after the current or next hour, and runs never overlap, so the
 * cached state needs no locking. The file is read through {@link SystemFileLoader}, which rounds this file to the hour,
 * so the offset leaves room for the importer to persist the file for the hour that just started.
 */
@CustomLog
@Named
@NullMarked
public final class ExchangeRateUpdater {

    private @Nullable Bytes cachedContents;
    private @Nullable ExchangeRateSet cachedRates;
    private final FileID exchangeRateFileId;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean();
    private final SystemFileLoader systemFileLoader;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private final Web3Properties web3Properties;

    public ExchangeRateUpdater(
            final SystemEntity systemEntity,
            final SystemFileLoader systemFileLoader,
            final TransactionExecutorFactory transactionExecutorFactory,
            final Web3Properties web3Properties) {
        this.exchangeRateFileId = Utils.toFileID(systemEntity.exchangeRateFile());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("exchange-rate-updater").daemon().factory());
        this.systemFileLoader = systemFileLoader;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.web3Properties = web3Properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        start(delayUntilNextRun(Instant.now()), web3Properties.getExchangeRateRefreshPeriod());
    }

    void start(final Duration delay, final Duration period) {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        scheduler.scheduleAtFixedRate(this::run, delay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    /** Delay from {@code now} until the refresh offset past the current hour, or one period after that if passed. */
    Duration delayUntilNextRun(final Instant now) {
        final var hour = now.truncatedTo(ChronoUnit.HOURS);
        var next = hour.plus(web3Properties.getExchangeRateRefreshOffset());
        if (next.isBefore(now)) {
            next = next.plus(web3Properties.getExchangeRateRefreshPeriod());
        }
        return Duration.between(now, next);
    }

    /** Not thread safe. Outside the scheduler thread, only call it when no scheduled run can overlap, as in tests. */
    void refresh(final Instant now) {
        load(now);
        var updated = 0;
        for (final var exchangeRateManager : transactionExecutorFactory.getExchangeRateManagers()) {
            if (apply(exchangeRateManager)) {
                ++updated;
            }
        }
        if (updated > 0) {
            log.info("Updated {} exchange rate managers to {}", updated, cachedRates);
        }
    }

    private boolean apply(final ExchangeRateManager exchangeRateManager) {
        if (cachedContents == null || cachedRates == null) {
            return false;
        }

        try {
            if (cachedRates.equals(exchangeRateManager.exchangeRates())) {
                return false;
            }

            exchangeRateManager.systemUpdate(cachedContents);
            return true;
        } catch (final HandleException e) {
            log.warn("Ignoring invalid exchange rates {}: {}", cachedRates, e.getStatus());
            cachedContents = null;
            cachedRates = null;
            return false;
        }
    }

    private void load(final Instant now) {
        final var file = systemFileLoader.load(
                exchangeRateFileId, DomainUtils.convertToNanos(now.getEpochSecond(), now.getNano()));
        if (file == null) {
            return;
        }

        final var contents = file.contents();
        if (cachedContents != null && contents.equals(cachedContents)) {
            return;
        }

        try {
            cachedRates = ExchangeRateSet.PROTOBUF.parse(contents);
            cachedContents = contents;
        } catch (final ParseException e) {
            log.warn("Unable to parse exchange rates: {}", e.getMessage());
        }
    }

    private void run() {
        try {
            refresh(Instant.now());
        } catch (final Exception e) {
            log.warn("Unable to refresh exchange rates: {}", e.getMessage());
        }
    }
}
