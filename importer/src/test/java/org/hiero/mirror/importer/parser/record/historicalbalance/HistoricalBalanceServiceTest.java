// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.historicalbalance;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.awaitility.Durations;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.importer.db.TimePartitionService;
import org.hiero.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import org.hiero.mirror.importer.parser.record.RecordFileParsedEvent;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.AccountBalanceRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.hiero.mirror.importer.repository.TokenBalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class HistoricalBalanceServiceTest {

    @Test
    void concurrentOnRecordFileParsed() {
        try (var pool = Executors.newFixedThreadPool(2)) {
            // given
            var accountBalanceFileRepository = mock(AccountBalanceFileRepository.class);
            var accountBalanceRepository = mock(AccountBalanceRepository.class);
            var balanceDownloaderProperties = mock(BalanceDownloaderProperties.class);
            var platformTransactionManager = mock(PlatformTransactionManager.class);
            var recordFileRepository = mock(RecordFileRepository.class);
            var timePartitionService = mock(TimePartitionService.class);
            var tokenBalanceRepository = mock(TokenBalanceRepository.class);

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
            var historicalBalanceProperties = new HistoricalBalanceProperties(balanceDownloaderProperties);
            var service = new HistoricalBalanceService(
                    accountBalanceFileRepository,
                    accountBalanceRepository,
                    new SimpleMeterRegistry(),
                    platformTransactionManager,
                    historicalBalanceProperties,
                    recordFileRepository,
                    new SystemEntity(new CommonProperties()),
                    timePartitionService,
                    tokenBalanceRepository);

            // when
            var event = new RecordFileParsedEvent(
                    this,
                    lastBalanceTimestamp
                            + historicalBalanceProperties.getMinFrequency().toNanos());
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
}
