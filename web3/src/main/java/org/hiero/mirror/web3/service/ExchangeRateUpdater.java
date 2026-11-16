// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.ParseException;
import jakarta.inject.Named;
import java.time.Instant;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.state.SystemFileLoader;
import org.hiero.mirror.web3.state.Utils;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Refreshes each executor's latest exchange rates after the current rate expires. Historical rates are selected
 * independently by {@link ExchangeRateManager} using the execution context.
 */
@CustomLog
@Named
@NullMarked
final class ExchangeRateUpdater {

    private final FileID exchangeRateFileId;
    private final SystemFileLoader systemFileLoader;
    private final TransactionExecutorFactory transactionExecutorFactory;
    private long expirationTime;

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
            if (expirationTime <= now.getEpochSecond()) {
                refresh(now);
            }
        } catch (final Exception e) {
            log.warn("Unable to refresh exchange rates: {}", e.getMessage());
        }
    }

    /** Applies the latest file without changing the historical exchange-rate cache. */
    synchronized void refresh(final Instant now) {
        final var managers = transactionExecutorFactory.getExchangeRateManagers();
        if (managers.isEmpty()) {
            return;
        }
        final var file = systemFileLoader.load(
                exchangeRateFileId, DomainUtils.convertToNanos(now.getEpochSecond(), now.getNano()));
        if (file == null) {
            return;
        }

        final ExchangeRateSet exchangeRates;
        try {
            exchangeRates = ExchangeRateSet.PROTOBUF.parseStrict(file.contents().toReadableSequentialData());
        } catch (final ParseException e) {
            log.warn("Unable to parse exchange rates: {}", e.getMessage());
            return;
        }
        if (!exchangeRates.hasCurrentRate()
                || !exchangeRates.currentRateOrThrow().hasExpirationTime()) {
            log.warn("Ignoring exchange rates without an expiration time: {}", exchangeRates);
            return;
        }

        var updated = 0;
        var refreshed = true;
        for (final var manager : managers) {
            try {
                if (!exchangeRates.equals(manager.exchangeRates())) {
                    manager.systemUpdate(file.contents());
                    ++updated;
                }
            } catch (final HandleException e) {
                refreshed = false;
                log.warn("Ignoring invalid exchange rates: {}", e.getStatus());
            }
        }
        if (refreshed) {
            expirationTime =
                    exchangeRates.currentRateOrThrow().expirationTimeOrThrow().seconds();
        }
        if (updated > 0) {
            log.info("Updated {} exchange rate managers from overall {}.", updated, managers.size());
        }
    }
}
