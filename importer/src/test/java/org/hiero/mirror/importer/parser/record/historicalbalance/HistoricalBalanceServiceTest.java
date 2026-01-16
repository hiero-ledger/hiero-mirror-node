// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.historicalbalance;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.awaitility.Durations;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.db.TimePartition;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import org.hiero.mirror.importer.parser.record.RecordFileParsedEvent;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.AccountBalanceRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TokenBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class HistoricalBalanceServiceTest {

    private AccountBalanceFileRepository accountBalanceFileRepository;
    private AccountBalanceRepository accountBalanceRepository;
    private RecordFileRepository recordFileRepository;
    private TimePartitionService timePartitionService;
    private EntityRepository entityRepository;
    private SystemEntity systemEntity;

    private HistoricalBalanceProperties properties;
    private HistoricalBalanceService service;

    @BeforeEach
    void setup() {
        // initialized dependencies
        var balanceDownloaderProperties = mock(BalanceDownloaderProperties.class);
        var platformTransactionManager = mock(PlatformTransactionManager.class);
        var tokenBalanceRepository = mock(TokenBalanceRepository.class);

        accountBalanceFileRepository = mock(AccountBalanceFileRepository.class);
        accountBalanceRepository = mock(AccountBalanceRepository.class);
        recordFileRepository = mock(RecordFileRepository.class);
        timePartitionService = mock(TimePartitionService.class);
        entityRepository = mock(EntityRepository.class);
        systemEntity = new SystemEntity(new CommonProperties());

        properties = new HistoricalBalanceProperties(balanceDownloaderProperties);

        service = new HistoricalBalanceService(
                accountBalanceFileRepository,
                accountBalanceRepository,
                new SimpleMeterRegistry(),
                platformTransactionManager,
                properties,
                recordFileRepository,
                systemEntity,
                timePartitionService,
                tokenBalanceRepository,
                entityRepository);
    }

    @Test
    void concurrentOnRecordFileParsed() {
        try (var pool = Executors.newFixedThreadPool(2)) {
            long lastBalanceTimestamp = 100;
            when(accountBalanceFileRepository.findLatest())
                    .thenReturn(Optional.of(AccountBalanceFile.builder()
                            .consensusTimestamp(lastBalanceTimestamp)
                            .build()));

            var semaphore = new Semaphore(0);
            when(recordFileRepository.findLatest()).thenAnswer(invocation -> {
                semaphore.acquire();
                return Optional.empty();
            });

            // when
            var event = new RecordFileParsedEvent(
                    this, lastBalanceTimestamp + properties.getMinFrequency().toNanos());
            Runnable runnable = () -> service.onRecordFileParsed(event);
            var task1 = pool.submit(runnable);
            var task2 = pool.submit(runnable);

            // then
            // verify that only one task is done
            await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                    .atMost(Durations.TWO_SECONDS)
                    .until(() -> task1.isDone() ^ task2.isDone());
            // unblock the remaining task
            semaphore.release();

            // then
            await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                    .atMost(Durations.TWO_SECONDS)
                    .until(() -> task1.isDone() && task2.isDone());
            // only one findLatest call
            verify(recordFileRepository).findLatest();

            // when
            // run it again
            var task3 = pool.submit(runnable);
            semaphore.release();

            // then
            await().pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                    .atMost(Durations.TWO_SECONDS)
                    .until(task3::isDone);
            verify(recordFileRepository, times(2)).findLatest();
        }
    }

    @Test
    void shouldCreateTreasuryAccountIfMissing() {
        var recordFile = RecordFile.builder().consensusEnd(100L).build();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(recordFile));
        when(entityRepository.existsById(systemEntity.treasuryAccount().getId()))
                .thenReturn(false);
        when(timePartitionService.getOverlappingTimePartitions(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(TimePartition.builder()
                        .timestampRange(Range.closedOpen(0L, 1000L))
                        .build()));

        long lastBalanceTimestamp = 50L;
        long consensusEnd = lastBalanceTimestamp + properties.getMinFrequency().toNanos();
        when(accountBalanceFileRepository.findLatest())
                .thenReturn(Optional.of(AccountBalanceFile.builder()
                        .consensusTimestamp(lastBalanceTimestamp)
                        .build()));

        assertFalse(service.getTreasuryExists().get());

        when(accountBalanceRepository.getMaxConsensusTimestampInRange(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(entityRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0)); // Succeed on second attempt

        var event = new RecordFileParsedEvent(this, consensusEnd);

        service.onRecordFileParsed(event);

        verify(entityRepository).existsById(systemEntity.treasuryAccount().getId());
        verify(entityRepository).save(any());
        assertTrue(service.getTreasuryExists().get());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void shouldResetTreasuryExistsOnFailureAndSucceedOnNextCall(CapturedOutput capture) {
        var recordFile = RecordFile.builder().consensusEnd(300L).build();
        when(recordFileRepository.findLatest()).thenReturn(Optional.of(recordFile));
        when(entityRepository.existsById(systemEntity.treasuryAccount().getId()))
                .thenReturn(false);
        when(timePartitionService.getOverlappingTimePartitions(anyString(), anyLong(), anyLong()))
                .thenReturn(List.of(TimePartition.builder()
                        .timestampRange(Range.closedOpen(0L, 1000L))
                        .build()));
        when(accountBalanceRepository.getMaxConsensusTimestampInRange(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        long lastBalanceTimestamp = 50L;
        long consensusEnd = lastBalanceTimestamp + properties.getMinFrequency().toNanos();
        when(accountBalanceFileRepository.findLatest())
                .thenReturn(Optional.of(AccountBalanceFile.builder()
                        .consensusTimestamp(lastBalanceTimestamp)
                        .build()));

        when(entityRepository.save(any()))
                .thenThrow(new RuntimeException("Simulated failure")) // Fail on first attempt
                .thenAnswer(invocation -> invocation.getArgument(0)); // Succeed on second attempt

        var event = new RecordFileParsedEvent(
                this,
                recordFile.getConsensusEnd() + properties.getMinFrequency().toNanos());

        // first call fails and sets treasuryExists to false
        service.onRecordFileParsed(event);
        verify(entityRepository, times(1))
                .existsById(systemEntity.treasuryAccount().getId());
        verify(entityRepository, times(1)).save(any());
        assertFalse(service.getTreasuryExists().get());
        assertTrue(capture.getOut().contains("Simulated failure"));
        assertTrue(capture.getOut().contains("Failed to generate historical balances in"));
        assertTrue(
                capture.getOut().contains("Failed to auto create treasury account " + systemEntity.treasuryAccount()));

        // second call is successful and sets treasuryExists to true
        service.onRecordFileParsed(event);
        verify(entityRepository, times(2))
                .existsById(systemEntity.treasuryAccount().getId());
        verify(entityRepository, times(2)).save(any());
        assertTrue(service.getTreasuryExists().get());
    }
}
