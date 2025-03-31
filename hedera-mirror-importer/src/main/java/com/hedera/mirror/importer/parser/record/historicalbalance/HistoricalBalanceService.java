// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.historicalbalance;

import static com.hedera.mirror.common.domain.balance.AccountBalanceFile.INVALID_NODE_ID;
import static com.hedera.mirror.importer.parser.AbstractStreamFileParser.STREAM_PARSE_DURATION_METRIC_NAME;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.SystemEntity;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.db.TimePartitionService;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.domain.StreamFilename.FileType;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.record.RecordFileParsedEvent;
import com.hedera.mirror.importer.parser.record.RecordFileParser;
import com.hedera.mirror.importer.repository.AccountBalanceFileRepository;
import com.hedera.mirror.importer.repository.AccountBalanceRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@ConditionalOnProperty(
        name = "enabled",
        matchIfMissing = true,
        prefix = "hedera.mirror.importer.parser.record.historical-balance")
@CustomLog
@Named
public class HistoricalBalanceService {

    private static final String ACCOUNT_BALANCE_TABLE_NAME = "account_balance";

    private final AccountBalanceFileRepository accountBalanceFileRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final HistoricalBalanceProperties properties;
    private final RecordFileRepository recordFileRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final SystemEntity systemEntity;
    private final TimePartitionService timePartitionService;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TransactionTemplate transactionTemplate;

    // metrics
    private final Timer generateDurationMetricFailure;
    private final Timer generateDurationMetricSuccess;

    @SuppressWarnings("java:S107")
    public HistoricalBalanceService(
            AccountBalanceFileRepository accountBalanceFileRepository,
            AccountBalanceRepository accountBalanceRepository,
            MeterRegistry meterRegistry,
            PlatformTransactionManager platformTransactionManager,
            HistoricalBalanceProperties properties,
            RecordFileRepository recordFileRepository,
            SystemEntity systemEntity,
            TimePartitionService timePartitionService,
            TokenBalanceRepository tokenBalanceRepository) {
        this.accountBalanceFileRepository = accountBalanceFileRepository;
        this.accountBalanceRepository = accountBalanceRepository;
        this.properties = properties;
        this.recordFileRepository = recordFileRepository;
        this.systemEntity = systemEntity;
        this.timePartitionService = timePartitionService;
        this.tokenBalanceRepository = tokenBalanceRepository;

        // Set repeatable read isolation level and transaction timeout
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(
                (int) properties.getTransactionTimeout().toSeconds());

        // metrics
        var timer = Timer.builder(STREAM_PARSE_DURATION_METRIC_NAME).tag("type", StreamType.BALANCE.toString());
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
    public void onRecordFileParsed(RecordFileParsedEvent event) {
        if (running.compareAndExchange(false, true)) {
            return;
        }

        var stopwatch = Stopwatch.createStarted();
        Timer timer = null;

        try {
            long consensusEnd = event.getConsensusEnd();
            if (!shouldGenerate(consensusEnd)) {
                return;
            }

            final long treasuryAccountId = systemEntity.treasuryAccount().getId();
            log.info("Generating historical balances after processing record file with consensusEnd {}", consensusEnd);
            transactionTemplate.executeWithoutResult(t -> {
                long loadStart = System.currentTimeMillis();
                long timestamp = recordFileRepository
                        .findLatest()
                        .map(RecordFile::getConsensusEnd)
                        // This should never happen since the function is triggered after a record file is parsed
                        .orElseThrow(() -> new ParserException("Record file table is empty"));

                var maxConsensusTimestamp = getMaxConsensusTimestamp(timestamp);
                boolean full = maxConsensusTimestamp.isEmpty();
                int accountBalancesCount;
                int tokenBalancesCount;
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

                long loadEnd = System.currentTimeMillis();
                String filename = StreamFilename.getFilename(
                        StreamType.BALANCE, FileType.DATA, Instant.ofEpochSecond(0, timestamp));
                var accountBalanceFile = AccountBalanceFile.builder()
                        .consensusTimestamp(timestamp)
                        .count((long) accountBalancesCount)
                        .loadStart(loadStart)
                        .loadEnd(loadEnd)
                        .name(filename)
                        .nodeId(INVALID_NODE_ID)
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
            });

            timer = generateDurationMetricSuccess;
        } catch (Exception e) {
            log.error("Failed to generate historical balances in {}", stopwatch, e);
            timer = generateDurationMetricFailure;
        } finally {
            running.set(false);

            if (timer != null) {
                timer.record(stopwatch.elapsed());
            }
        }
    }

    private Optional<Long> getMaxConsensusTimestamp(long timestamp) {
        var partitions =
                timePartitionService.getOverlappingTimePartitions(ACCOUNT_BALANCE_TABLE_NAME, timestamp, timestamp);
        if (partitions.isEmpty()) {
            throw new InvalidDatasetException(
                    String.format("No account_balance partition found for timestamp %s", timestamp));
        }

        long treasuryAccountId = systemEntity.treasuryAccount().getId();
        var partitionRange = partitions.getFirst().getTimestampRange();
        return accountBalanceRepository.getMaxConsensusTimestampInRange(
                partitionRange.lowerEndpoint(), partitionRange.upperEndpoint(), treasuryAccountId);
    }

    private boolean shouldGenerate(long consensusEnd) {
        return properties.isEnabled()
                && accountBalanceFileRepository
                        .findLatest()
                        .map(AccountBalanceFile::getConsensusTimestamp)
                        .or(() -> recordFileRepository
                                .findFirst()
                                .map(RecordFile::getConsensusEnd)
                                .map(timestamp ->
                                        timestamp + properties.getInitialDelay().toNanos()))
                        .filter(lastTimestamp -> consensusEnd - lastTimestamp
                                >= properties.getMinFrequency().toNanos())
                        .isPresent();
    }
}
