// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.historicalbalance;

import static org.hiero.mirror.importer.parser.AbstractStreamFileParser.STREAM_PARSE_DURATION_METRIC_NAME;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.domain.StreamFilename.FileType;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.record.RecordFileParsedEvent;
import org.hiero.mirror.importer.parser.record.RecordFileParser;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.AccountBalanceRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TokenBalanceRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@CustomLog
@Named
class HistoricalBalanceService {

    static final long NO_CACHED_TIMESTAMP = -1L;

    private static final String ACCOUNT_BALANCE_TABLE_NAME = "account_balance";

    @Getter(AccessLevel.PACKAGE)
    private final AtomicBoolean treasuryExists = new AtomicBoolean(false);

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final HistoricalBalanceProperties properties;
    private final RecordFileRepository recordFileRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final SystemEntity systemEntity;
    private final TimePartitionService timePartitionService;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TransactionTemplate transactionTemplate;
    private final EntityRepository entityRepository;

    // metrics
    private final Timer generateDurationMetricFailure;
    private final Timer generateDurationMetricSuccess;

    @Setter(AccessLevel.PACKAGE)
    private volatile long lastAccountBalanceTimestamp = NO_CACHED_TIMESTAMP;

    @SuppressWarnings("java:S107")
    HistoricalBalanceService(
            final AccountBalanceFileRepository accountBalanceFileRepository,
            final AccountBalanceRepository accountBalanceRepository,
            final MeterRegistry meterRegistry,
            final PlatformTransactionManager platformTransactionManager,
            final HistoricalBalanceProperties properties,
            final RecordFileRepository recordFileRepository,
            final SystemEntity systemEntity,
            final TimePartitionService timePartitionService,
            final TokenBalanceRepository tokenBalanceRepository,
            final EntityRepository entityRepository) {
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        this.accountBalanceRepository = accountBalanceRepository;
        this.properties = properties;
        this.recordFileRepository = recordFileRepository;
        this.systemEntity = systemEntity;
        this.timePartitionService = timePartitionService;
        this.tokenBalanceRepository = tokenBalanceRepository;
        this.entityRepository = entityRepository;

        // Set repeatable read isolation level and transaction timeout
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(
                (int) properties.getTransactionTimeout().toSeconds());

        // metrics
        final var timer = Timer.builder(STREAM_PARSE_DURATION_METRIC_NAME).tag("type", StreamType.BALANCE.toString());
        generateDurationMetricFailure = timer.tag("success", "false").register(meterRegistry);
        generateDurationMetricSuccess = timer.tag("success", "true").register(meterRegistry);
    }

    /**
     * Listens on {@link RecordFileParsedEvent} and generate historical balance at configured frequency.
     *
     * @param event The record file parsed event published by {@link RecordFileParser}
     */
    @Async
    @TransactionalEventListener
    public void onRecordFileParsed(final RecordFileParsedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }

        if (running.compareAndExchange(false, true)) {
            return;
        }

        final var stopwatch = Stopwatch.createStarted();
        Timer timer = null;

        try {
            final long consensusEnd = event.getConsensusEnd();
            if (!shouldGenerate(consensusEnd)) {
                return;
            }

            final long treasuryAccountId = systemEntity.treasuryAccount().getId();
            checkTreasuryAccount();

            log.info("Generating historical balances after processing record file with consensusEnd {}", consensusEnd);
            final var generatedTimestamp = transactionTemplate.execute(_ -> {
                final long loadStart = System.currentTimeMillis();
                final long timestamp = recordFileRepository
                        .findLatest()
                        .map(RecordFile::getConsensusEnd)
                        // This should never happen since the function is triggered after a record file is parsed
                        .orElseThrow(() -> new ParserException("Record file table is empty"));

                final var maxConsensusTimestamp = getMaxConsensusTimestamp(timestamp);
                final boolean full = maxConsensusTimestamp.isEmpty();
                final int accountBalancesCount;
                final int tokenBalancesCount;
                if (full) {
                    // get a full snapshot
                    accountBalancesCount = accountBalanceRepository.balanceSnapshot(timestamp, treasuryAccountId);
                    tokenBalancesCount = properties.isTokenBalances()
                            ? tokenBalanceRepository.balanceSnapshot(timestamp, treasuryAccountId)
                            : 0;
                } else {
                    // get a snapshot that has no duplicates
                    accountBalancesCount = accountBalanceRepository.balanceSnapshotDeduplicate(
                            maxConsensusTimestamp.get(), timestamp, treasuryAccountId);
                    tokenBalancesCount = properties.isTokenBalances()
                            ? tokenBalanceRepository.balanceSnapshotDeduplicate(
                                    maxConsensusTimestamp.get(), timestamp, treasuryAccountId)
                            : 0;
                }

                final long loadEnd = System.currentTimeMillis();
                final var filename = StreamFilename.getFilename(
                        StreamType.BALANCE, FileType.DATA, Instant.ofEpochSecond(0, timestamp));
                final var accountBalanceFile = AccountBalanceFile.builder()
                        .consensusTimestamp(timestamp)
                        .count((long) accountBalancesCount)
                        .loadStart(loadStart)
                        .loadEnd(loadEnd)
                        .name(filename)
                        .synthetic(true)
                        .build();
                accountBalanceFileRepository.save(accountBalanceFile);

                log.info(
                        "Generated {} historical account balance file {} with {} account balances and {} token balances in {}",
                        full ? "full" : "deduped",
                        filename,
                        accountBalancesCount,
                        tokenBalancesCount,
                        stopwatch);
                return timestamp;
            });

            lastAccountBalanceTimestamp = Objects.requireNonNull(generatedTimestamp);
            timer = generateDurationMetricSuccess;
        } catch (final Exception e) {
            log.error("Failed to generate historical balances in {}", stopwatch, e);
            timer = generateDurationMetricFailure;
        } finally {
            running.set(false);

            if (timer != null) {
                timer.record(stopwatch.elapsed());
            }
        }
    }

    private Optional<Long> getMaxConsensusTimestamp(final long timestamp) {
        final var partitions =
                timePartitionService.getOverlappingTimePartitions(ACCOUNT_BALANCE_TABLE_NAME, timestamp, timestamp);
        if (partitions.isEmpty()) {
            throw new InvalidDatasetException(
                    String.format("No account_balance partition found for timestamp %s", timestamp));
        }

        final long treasuryAccountId = systemEntity.treasuryAccount().getId();
        final var partitionRange = partitions.getFirst().getTimestampRange();
        return accountBalanceRepository.getMaxConsensusTimestampInRange(
                partitionRange.lowerEndpoint(), partitionRange.upperEndpoint(), treasuryAccountId);
    }

    private boolean shouldGenerate(final long consensusEnd) {
        final long lastTimestamp;
        if (lastAccountBalanceTimestamp != NO_CACHED_TIMESTAMP) {
            lastTimestamp = lastAccountBalanceTimestamp;
        } else {
            // Only query database to find the last timestamp to compare to when not cached yet
            final var timestamp = accountBalanceFileRepository
                    .findLatest()
                    .map(AccountBalanceFile::getConsensusTimestamp)
                    .or(() -> recordFileRepository
                            .findFirst()
                            .map(RecordFile::getConsensusEnd)
                            .map(t -> t + properties.getInitialDelay().toNanos()));
            if (timestamp.isEmpty()) {
                return false;
            }

            lastTimestamp = timestamp.get();
        }

        return consensusEnd - lastTimestamp >= properties.getMinFrequency().toNanos();
    }

    private void checkTreasuryAccount() {
        if (treasuryExists.compareAndSet(false, true)) {
            final var treasuryAccountEntityId = systemEntity.treasuryAccount();
            try {
                if (!entityRepository.existsById(treasuryAccountEntityId.getId())) {
                    final var savedTreasuryAccount =
                            entityRepository.save(treasuryAccountEntityId.toEntity().toBuilder()
                                    .balance(0L)
                                    .createdTimestamp(0L)
                                    .declineReward(true)
                                    .deleted(false)
                                    .memo("Mirror node created synthetic treasury account")
                                    .type(EntityType.ACCOUNT)
                                    .timestampRange(Range.atLeast(0L))
                                    .build());
                    log.info("Created sentinel treasury account: {}", savedTreasuryAccount);
                }
            } catch (final Exception e) {
                log.error("Failed to auto create treasury account {}", treasuryAccountEntityId);
                treasuryExists.set(false);
                throw e;
            }
        }
    }
}
