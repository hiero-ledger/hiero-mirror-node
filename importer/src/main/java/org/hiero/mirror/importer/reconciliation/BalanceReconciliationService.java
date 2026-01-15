// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reconciliation;

import static org.hiero.mirror.common.domain.job.ReconciliationStatus.FAILURE_CRYPTO_TRANSFERS;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.FAILURE_FIFTY_BILLION;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.FAILURE_TOKEN_TRANSFERS;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.FAILURE_UNKNOWN;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.RUNNING;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.SUCCESS;
import static org.hiero.mirror.common.domain.job.ReconciliationStatus.UNKNOWN;
import static org.hiero.mirror.importer.reconciliation.ReconciliationProperties.RemediationStrategy.ACCUMULATE;
import static org.hiero.mirror.importer.reconciliation.ReconciliationProperties.RemediationStrategy.FAIL;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Uninterruptibles;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.Builder;
import lombok.CustomLog;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.job.ReconciliationJob;
import org.hiero.mirror.common.domain.job.ReconciliationStatus;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.ReconciliationJobRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;

@CustomLog
@Named
class BalanceReconciliationService {

    static final long FIFTY_BILLION_HBARS = 50_000_000_000L * 100_000_000L;
    static final String METRIC = "hiero.mirror.importer.reconciliation";

    // Due to the number of rows returned, it's considerably more performant to not use JPA
    private static final String BALANCE_QUERY =
            "select account_id, balance from account_balance " + "where consensus_timestamp = ?";

    private static final String CRYPTO_TRANSFER_QUERY = """
                    select entity_id, sum(amount) balance from crypto_transfer
                    where consensus_timestamp > ? and consensus_timestamp <= ? and (errata is null or errata <> 'DELETE')
                    group by entity_id""";

    private static final String TOKEN_BALANCE_QUERY =
            "select account_id, token_id, balance from token_balance " + "where consensus_timestamp = ?";

    private static final String TOKEN_TRANSFER_QUERY = """
                    select account_id, token_id, sum(amount) as balance
                    from token_transfer where consensus_timestamp > ? and consensus_timestamp <= ?
                    group by token_id, account_id""";

    final AtomicReference<ReconciliationStatus> status;

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final JdbcOperations jdbcOperations;
    private final RecordFileRepository recordFileRepository;
    private final ReconciliationProperties reconciliationProperties;
    private final ReconciliationJobRepository reconciliationJobRepository;

    BalanceReconciliationService(
            AccountBalanceFileRepository accountBalanceFileRepository,
            JdbcOperations jdbcOperations,
            MeterRegistry meterRegistry,
            RecordFileRepository recordFileRepository,
            ReconciliationProperties reconciliationProperties,
            ReconciliationJobRepository reconciliationJobRepository) {
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        this.jdbcOperations = jdbcOperations;
        this.recordFileRepository = recordFileRepository;
        this.reconciliationProperties = reconciliationProperties;
        this.reconciliationJobRepository = reconciliationJobRepository;
        this.status = meterRegistry.gauge(
                METRIC, new AtomicReference<>(UNKNOWN), s -> s.get().ordinal());
    }

    @Scheduled(cron = "${hiero.mirror.importer.reconciliation.cron:0 0 0 * * *}")
    @SuppressWarnings("java:S3776")
    public synchronized void reconcile() {
        if (!reconciliationProperties.isEnabled()) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        var reconciliationJob = getLatestJob();

        try {
            log.info(
                    "Reconciling balance files between {} and {} with {} remediation strategy",
                    Instant.ofEpochSecond(0, reconciliationJob.getConsensusTimestamp()),
                    reconciliationProperties.getEndDate(),
                    reconciliationProperties.getRemediationStrategy());
            var previous = getNextBalanceSnapshot(reconciliationJob, Optional.empty());

            if (previous.isEmpty()) {
                log.info("No balance files to process");
                reconciliationJob.setStatus(UNKNOWN);
                return;
            }

            var current = getNextBalanceSnapshot(reconciliationJob, previous);

            while (current.isPresent()) {
                reconcile(previous.get(), current.get());
                reconciliationJob.increment();

                if (!reconciliationJob.hasErrors()) {
                    var consensusTimestamp =
                            current.get().getAccountBalanceFile().getConsensusTimestamp();
                    reconciliationJob.setConsensusTimestamp(consensusTimestamp);

                    // Periodically update progress
                    if (reconciliationJob.getCount() % 100 == 0) {
                        reconciliationJobRepository.save(reconciliationJob);
                    }
                }

                if (reconciliationProperties.getRemediationStrategy() == ACCUMULATE) {
                    previous = Optional.of(current.get().toBuilder()
                            .balances(previous.get().getBalances())
                            .tokenBalances(previous.get().getTokenBalances())
                            .build());
                } else {
                    previous = current;
                }

                current = getNextBalanceSnapshot(reconciliationJob, previous);
            }

            if (reconciliationJob.hasErrors()) {
                log.info("Reconciled {} balance files with some errors in {}", reconciliationJob.getCount(), stopwatch);
            } else {
                reconciliationJob.setStatus(SUCCESS);
                log.info("Reconciled {} balance files successfully in {}", reconciliationJob.getCount(), stopwatch);
            }
        } catch (Exception e) {
            var errorStatus = e instanceof ReconciliationException re ? re.getStatus() : FAILURE_UNKNOWN;
            reconciliationJob.setError(e.getMessage());
            reconciliationJob.setStatus(errorStatus);
            log.warn(
                    "Reconciliation completed unsuccessfully after {} balance files in {}: {}",
                    reconciliationJob.getCount(),
                    stopwatch,
                    e.getMessage());
        } finally {
            reconciliationJob.setTimestampEnd(Instant.now());
            reconciliationJobRepository.save(reconciliationJob);
            status.set(reconciliationJob.getStatus());
        }
    }

    private ReconciliationJob getLatestJob() {
        long startDate = DomainUtils.convertToNanosMax(reconciliationProperties.getStartDate());
        long consensusTimestamp = reconciliationJobRepository
                .findLatest()
                .map(ReconciliationJob::getConsensusTimestamp)
                .orElse(startDate);
        consensusTimestamp = Math.max(startDate, consensusTimestamp);

        var reconciliationJob = ReconciliationJob.builder()
                .consensusTimestamp(consensusTimestamp)
                .count(0)
                .error("")
                .status(RUNNING)
                .timestampStart(Instant.now())
                .build();

        status.set(reconciliationJob.getStatus());
        return reconciliationJobRepository.save(reconciliationJob);
    }

    private void reconcile(BalanceSnapshot previous, BalanceSnapshot current) {
        reconcileCryptoTransfers(previous, current);
        reconcileTokenTransfers(previous, current);

        long elapsed = System.currentTimeMillis() - current.getStartTime();
        String name = current.getAccountBalanceFile().getName();
        log.info(
                "Reconciled balance file {} with {} balances and {} token balances in {} ms",
                name,
                current.getBalances().size(),
                current.getTokenBalances().size(),
                elapsed);

        if (Duration.ZERO.compareTo(reconciliationProperties.getDelay()) < 0) {
            Uninterruptibles.sleepUninterruptibly(reconciliationProperties.getDelay());
        }
    }

    private void reconcileCryptoTransfers(BalanceSnapshot previous, BalanceSnapshot current) {
        var transfersBalance = previous.getBalances();

        jdbcOperations.query(
                CRYPTO_TRANSFER_QUERY,
                rs -> {
                    long accountId = rs.getLong(1);
                    long balance = rs.getLong(2);
                    transfersBalance.merge(accountId, balance, Math::addExact);
                },
                previous.getTimestamp(),
                current.getTimestamp());

        reconcileTransfers(FAILURE_CRYPTO_TRANSFERS, BalanceSnapshot::getBalances, previous, current);
    }

    private void reconcileTokenTransfers(BalanceSnapshot previous, BalanceSnapshot current) {
        if (!reconciliationProperties.isToken()) {
            return;
        }

        var tokenBalances = previous.getTokenBalances();

        jdbcOperations.query(
                TOKEN_TRANSFER_QUERY,
                rs -> {
                    long accountId = rs.getLong(1);
                    long tokenId = rs.getLong(2);
                    long balance = rs.getLong(3);
                    var tokenAccountId = new TokenAccountId(accountId, tokenId);
                    tokenBalances.merge(tokenAccountId, balance, Math::addExact);
                },
                previous.getTimestamp(),
                current.getTimestamp());

        reconcileTransfers(FAILURE_TOKEN_TRANSFERS, BalanceSnapshot::getTokenBalances, previous, current);
    }

    private <K> void reconcileTransfers(
            ReconciliationStatus failureStatus,
            Function<BalanceSnapshot, Map<K, Long>> mapper,
            BalanceSnapshot previous,
            BalanceSnapshot current) {
        var transfersBalance = mapper.apply(previous);
        var currentBalances = mapper.apply(current);

        if (!equals(transfersBalance, currentBalances)) {
            long fromTimestamp = previous.getTimestamp();
            long toTimestamp = current.getTimestamp();
            var difference = Maps.difference(transfersBalance, currentBalances);

            if (reconciliationProperties.getRemediationStrategy() == FAIL) {
                throw new ReconciliationException(failureStatus, fromTimestamp, toTimestamp, difference);
            }

            var error = String.format(failureStatus.getMessage(), fromTimestamp, toTimestamp, difference);
            log.warn(error);

            var reconciliationJob = previous.getReconciliationJob();
            reconciliationJob.setError(StringUtils.joinWith("\n", reconciliationJob.getError(), error));
            reconciliationJob.setStatus(failureStatus);
        }
    }

    private <T> boolean equals(Map<T, Long> previous, Map<T, Long> current) {
        for (var previousEntry : previous.entrySet()) {
            long currentValue = current.getOrDefault(previousEntry.getKey(), 0L);
            if (previousEntry.getValue() != currentValue) {
                return false;
            }
        }
        for (var currentEntry : current.entrySet()) {
            long previousValue = previous.getOrDefault(currentEntry.getKey(), 0L);
            if (currentEntry.getValue() != previousValue) {
                return false;
            }
        }
        return true;
    }

    private Optional<BalanceSnapshot> getNextBalanceSnapshot(
            ReconciliationJob reconciliationJob, Optional<BalanceSnapshot> previous) {

        long startTime = System.currentTimeMillis();
        long toTimestamp = DomainUtils.convertToNanosMax(reconciliationProperties.getEndDate());
        long fromTimestamp = previous.map(BalanceSnapshot::getAccountBalanceFile)
                .map(AccountBalanceFile::getConsensusTimestamp)
                .map(t -> t + 1L)
                .orElseGet(reconciliationJob::getConsensusTimestamp);

        return accountBalanceFileRepository
                .findNextInRange(fromTimestamp, toTimestamp)
                .map(accountBalanceFile -> {
                    var timestamp = accountBalanceFile.getConsensusTimestamp();
                    var balances = getAccountBalances(accountBalanceFile);
                    var recordFile = recordFileRepository.findNextBetween(timestamp - 1L, Long.MAX_VALUE);
                    var tokenBalances = getTokenBalances(accountBalanceFile);
                    return new BalanceSnapshot(
                            accountBalanceFile, balances, recordFile, reconciliationJob, startTime, tokenBalances);
                });
    }

    private Map<Long, Long> getAccountBalances(AccountBalanceFile accountBalanceFile) {
        Map<Long, Long> balances = new HashMap<>();
        AtomicLong total = new AtomicLong(0L);
        long consensusTimestamp = accountBalanceFile.getConsensusTimestamp();

        jdbcOperations.query(
                BALANCE_QUERY,
                rs -> {
                    long accountId = rs.getLong(1);
                    long balance = rs.getLong(2);
                    balances.put(accountId, balance);
                    total.addAndGet(balance);
                },
                consensusTimestamp);

        if (total.get() != FIFTY_BILLION_HBARS) {
            String name = accountBalanceFile.getName();
            throw new ReconciliationException(FAILURE_FIFTY_BILLION, name, total.get());
        }

        return balances;
    }

    private Map<TokenAccountId, Long> getTokenBalances(AccountBalanceFile accountBalanceFile) {
        if (!reconciliationProperties.isToken()) {
            return Collections.emptyMap();
        }

        Map<TokenAccountId, Long> balances = new HashMap<>();
        long consensusTimestamp = accountBalanceFile.getConsensusTimestamp();

        jdbcOperations.query(
                TOKEN_BALANCE_QUERY,
                rs -> {
                    long accountId = rs.getLong(1);
                    long tokenId = rs.getLong(2);
                    long balance = rs.getLong(3);
                    var tokenAccountId = new TokenAccountId(accountId, tokenId);
                    balances.put(tokenAccountId, balance);
                },
                consensusTimestamp);

        return balances;
    }

    @Value
    static class TokenAccountId {
        private final long accountId;
        private final long tokenId;
    }

    @Builder(toBuilder = true)
    @Value
    private static class BalanceSnapshot {

        private final AccountBalanceFile accountBalanceFile;
        private final Map<Long, Long> balances;
        private final Optional<RecordFile> recordFile;
        private final ReconciliationJob reconciliationJob;
        private final long startTime;
        private final Map<TokenAccountId, Long> tokenBalances;

        private long getTimestamp() {
            return accountBalanceFile.getConsensusTimestamp() + accountBalanceFile.getTimeOffset();
        }
    }
}
