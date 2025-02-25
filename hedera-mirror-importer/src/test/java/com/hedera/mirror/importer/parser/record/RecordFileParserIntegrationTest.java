// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.topic.StreamMessage;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.domain.RecordFileBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveSubscription.Message;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import reactor.test.StepVerifier;

@RequiredArgsConstructor
class RecordFileParserIntegrationTest extends ImporterIntegrationTest {

    private final CryptoTransferRepository cryptoTransferRepository;
    private final EntityRepository entityRepository;
    private final ReactiveRedisOperations<String, StreamMessage> reactiveRedisOperations;
    private final RecordFileBuilder recordFileBuilder;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordFileParser recordFileParser;
    private final RecordFileRepository recordFileRepository;
    private final TransactionRepository transactionRepository;

    @BeforeEach
    void setup() {
        recordFileParser.clear();
    }

    @Test
    void parse() {
        // given
        int transactions = 100;
        int entities = 50;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).type(TransactionType.CRYPTOTRANSFER));
        var recordFile1 = recordFileTemplate.build();
        var recordFile2 = recordFileTemplate.build();

        // when
        recordFileParser.parse(recordFile1);
        recordFileParser.parse(recordFile2);

        // then
        assertRecordFile(recordFile1, recordFile2);
        assertThat(cryptoTransferRepository.count()).isEqualTo(2 * 6 * transactions);
        assertThat(entityRepository.count()).isZero(); // Partial entities ignored
        assertThat(transactionRepository.count()).isEqualTo(2 * transactions);
    }

    @Test
    void parseList() {
        // given
        int transactions = 100;
        int entities = 50;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).type(TransactionType.CRYPTOTRANSFER));
        var recordFile1 = recordFileTemplate.build();
        var recordFile2 = recordFileTemplate.build();

        // when
        recordFileParser.parse(List.of(recordFile1, recordFile2));

        // then
        assertRecordFile(recordFile1, recordFile2);
        assertThat(cryptoTransferRepository.count()).isEqualTo(2 * 6 * transactions);
        assertThat(transactionRepository.count()).isEqualTo(2 * transactions);
    }

    @Test
    void parseSingleThenList() {
        // given
        int transactions = 100;
        int entities = 50;
        var recordFileTemplate = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(transactions).entities(entities).type(TransactionType.CRYPTOTRANSFER));
        var recordFile1 = recordFileTemplate.build();
        var recordFile2 = recordFileTemplate.build();
        var recordFile3 = recordFileTemplate.build();

        // when
        recordFileParser.parse(recordFile1);
        recordFileParser.parse(List.of(recordFile2, recordFile3));

        // then
        assertRecordFile(recordFile1, recordFile2, recordFile3);
        assertThat(cryptoTransferRepository.count()).isEqualTo(3 * 6 * transactions);
        assertThat(transactionRepository.count()).isEqualTo(3 * transactions);
    }

    @Test
    void topicMessage() {
        int count = 3;
        var topicMessage = recordItemBuilder.consensusSubmitMessage();
        var topicId = EntityId.of(topicMessage
                .build()
                .getTransactionBody()
                .getConsensusSubmitMessage()
                .getTopicID());
        var recordFile = recordFileBuilder
                .recordFile()
                .recordItems(i -> i.count(count).template(() -> topicMessage))
                .build();

        var receive = reactiveRedisOperations
                .listenToChannel("topic." + topicId.getId())
                .map(Message::getMessage);
        StepVerifier.create(receive)
                .thenAwait(java.time.Duration.ofSeconds(1L))
                .then(() -> recordFileParser.parse(recordFile))
                .thenAwait(java.time.Duration.ofMillis(500L))
                .expectNextCount(count)
                .thenCancel()
                .verify(java.time.Duration.ofMillis(2000L));
        assertRecordFile(recordFile);
    }

    @Test
    @EnabledIfV1
    void rollback() {
        // when
        var recordFileTemplate = recordFileBuilder.recordFile().recordItem(TransactionType.CRYPTOTRANSFER);
        var recordFile1 = recordFileTemplate.build();
        var items = recordFile1.getItems();
        recordFileParser.parse(recordFile1);

        // then
        assertRecordFile(recordFile1);

        // when
        var recordFile2 = recordFileTemplate.build();
        recordFile2.setItems(items); // Re-processing same transactions should result in duplicate keys
        Assertions.assertThrows(ParserException.class, () -> recordFileParser.parse(recordFile2));

        // then
        assertRecordFile(recordFile1);
        assertThat(retryRecorder.getRetries(ParserException.class)).isEqualTo(2);
    }

    private void assertRecordFile(RecordFile... recordFiles) {
        assertThat(recordFileRepository.findAll())
                .hasSize(recordFiles.length)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("bytes", "items", "logsBloom", "sidecars")
                .containsExactlyInAnyOrder(recordFiles)
                .allSatisfy(rf -> {
                    assertThat(rf.getLoadStart()).isPositive();
                    assertThat(rf.getLoadEnd()).isPositive();
                    assertThat(rf.getLoadEnd()).isGreaterThanOrEqualTo(rf.getLoadStart());
                });
    }
}
