// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;
import java.time.Duration;
import java.util.List;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.importer.parser.balance.AccountBalanceFileParser;
import org.hiero.mirror.importer.parser.balance.BalanceParserProperties;
import org.hiero.mirror.importer.parser.record.RecordFileParser;
import org.hiero.mirror.importer.parser.record.RecordParserProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@CustomLog
@ExtendWith(MockitoExtension.class)
class BatchStreamFileNotifierTest {

    private final BalanceParserProperties balanceParserProperties = new BalanceParserProperties();
    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final RecordParserProperties recordParserProperties = new RecordParserProperties();

    @Mock
    private AccountBalanceFileParser accountBalanceFileParser;

    @Mock
    private RecordFileParser recordFileParser;

    private BatchStreamFileNotifier notifier;

    @BeforeEach
    void setup() {
        when(accountBalanceFileParser.getProperties()).thenReturn(balanceParserProperties);
        when(recordFileParser.getProperties()).thenReturn(recordParserProperties);
        balanceParserProperties.setFrequency(Duration.ofMillis(1L));
        balanceParserProperties.getBatch().setMaxFiles(Integer.MAX_VALUE);
        recordParserProperties.setFrequency(Duration.ofMillis(1L));
        recordParserProperties.getBatch().setMaxFiles(Integer.MAX_VALUE);
        notifier = new BatchStreamFileNotifier(accountBalanceFileParser, recordFileParser);
    }

    @AfterEach
    void teardown() {
        notifier.close();
    }

    @Test
    void balanceFile() {
        var balanceFile = domainBuilder.accountBalanceFile().get();
        notifier.verified(balanceFile);
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(500L));
        verify(accountBalanceFileParser).parse(balanceFile);
    }

    @Test
    void recordFile() {
        var recordFile = domainBuilder.recordFile().get();
        notifier.verified(recordFile);
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(500L));
        verify(recordFileParser).parse(recordFile);
    }

    @Test
    void maxFilesReached() {
        recordParserProperties.getBatch().setMaxFiles(1);
        var recordFile1 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(0L).count(1L))
                .get();
        var recordFile2 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(0L).count(1L))
                .get();

        notifier.verified(recordFile1);
        notifier.verified(recordFile2);

        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(200L));
        verify(recordFileParser).parse(recordFile1);
        verify(recordFileParser).parse(recordFile2);
    }

    @Test
    void maxItemsReached() {
        recordParserProperties.getBatch().setMaxItems(2L);
        var recordFile1 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(0L).count(1L))
                .get();
        var recordFile2 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(0L).count(1L))
                .get();

        notifier.verified(recordFile1);
        notifier.verified(recordFile2);

        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(200L));
        verify(recordFileParser).parse(List.of(recordFile1, recordFile2));
    }

    @Test
    void flushInterval() {
        recordParserProperties.setFrequency(Duration.ofMillis(1000L));
        recordParserProperties.getBatch().setFlushInterval(Duration.ofMillis(100L));
        var recordFile1 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(0L).count(1L))
                .get();
        var recordFile2 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(0L).count(1L))
                .get();

        notifier.verified(recordFile1);
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(200L));
        notifier.verified(recordFile2);

        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(200L));
        verify(recordFileParser).parse(List.of(recordFile1, recordFile2));
    }

    @Test
    void flushIntervalNoFiles() {
        recordParserProperties.getBatch().setFlushInterval(Duration.ofMillis(500L));
        var recordFile1 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(0L).count(1L))
                .get();
        var recordFile2 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(0L).count(1L))
                .get();

        notifier.verified(recordFile1);
        notifier.verified(recordFile2);
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(600L));

        verify(recordFileParser).parse(List.of(recordFile1, recordFile2));
    }

    @Test
    void errorRecovers() {
        var recordFile = domainBuilder.recordFile().get();
        doThrow(new RuntimeException("Some error")).when(recordFileParser).parse(recordFile);
        notifier.verified(recordFile);
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(200L));
        verify(recordFileParser).parse(recordFile);
    }

    @Test
    void queueCapacityReached() {
        recordParserProperties.getBatch().setQueueCapacity(1);
        recordParserProperties.getBatch().setMaxFiles(1);
        notifier.close();
        notifier = new BatchStreamFileNotifier(accountBalanceFileParser, recordFileParser);
        var recordFile1 = domainBuilder.recordFile().get();
        var recordFile2 = domainBuilder.recordFile().get();
        var recordFile3 = domainBuilder.recordFile().get();
        var stopwatch = Stopwatch.createStarted();
        var sleep = Duration.ofMillis(500L);

        doAnswer(invocation -> {
                    Uninterruptibles.sleepUninterruptibly(sleep);
                    return null;
                })
                .when(recordFileParser)
                .parse(recordFile1);

        notifier.verified(recordFile1);
        notifier.verified(recordFile2);
        notifier.verified(recordFile3);
        assertThat(stopwatch.elapsed()).isGreaterThanOrEqualTo(sleep);

        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(200L));
        verify(recordFileParser).parse(recordFile1);
        verify(recordFileParser).parse(recordFile2);
    }
}
