// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.awaitility.Durations;
import org.hiero.mirror.importer.PubSubIntegrationTest;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.parser.record.RecordFileParser;
import org.hiero.mirror.importer.reader.record.RecordFileReader;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@RequiredArgsConstructor
class PubSubRecordParserTest extends PubSubIntegrationTest {

    private static final int NUM_TXNS = 34; // number of transactions in test record files

    @Value("classpath:data/pubsub-messages.txt")
    private final Path pubSubMessages;

    private final RecordFileParser recordFileParser;
    private final RecordFileReader recordFileReader;

    @Value("classpath:data/recordstreams/v2/record0.0.3/*.rcd")
    private final Resource[] testFiles;

    @Test
    void testPubSubExporter() throws Exception {
        for (int index = 0; index < testFiles.length; index++) {
            RecordFile recordFile = recordFileReader.read(StreamFileData.from(testFiles[index].getFile()));
            recordFile.setIndex((long) index);
            recordFile.setNodeId(0L);
            recordFileParser.parse(recordFile);
        }

        // then
        List<String> expectedMessages = Files.readAllLines(pubSubMessages);
        List<PubsubMessage> subscriberMessages = new ArrayList<>();
        await().atMost(Durations.TEN_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .until(() -> {
                    subscriberMessages.addAll(getAllMessages(NUM_TXNS));
                    return subscriberMessages.size() == NUM_TXNS;
                });

        List<String> actualMessages = subscriberMessages.stream()
                .map(PubsubMessage::getData)
                .map(ByteString::toStringUtf8)
                .toList();

        // map timestamps to messages and compare individual message JSON strings
        Map<Long, String> expectedMessageMap = mapMessages(expectedMessages);
        Map<Long, String> actualMessageMap = mapMessages(actualMessages);
        assertThat(actualMessageMap).hasSameSizeAs(actualMessages);
        assertThat(expectedMessageMap).hasSameSizeAs(expectedMessages);
        for (Map.Entry<Long, String> messageEntry : expectedMessageMap.entrySet()) {
            long consensusTimestamp = messageEntry.getKey();
            String expectedMessage = messageEntry.getValue();
            String actualMessage = actualMessageMap.get(consensusTimestamp);
            JSONAssert.assertEquals(
                    String.format("%d", consensusTimestamp), expectedMessage, actualMessage, JSONCompareMode.STRICT);
        }
    }

    public Map<Long, String> mapMessages(List<String> inputMessages) {
        // given message records text. Extract timestamp as a key and string as value
        Map<Long, String> messages = new HashMap<>();
        Pattern pattern = Pattern.compile("\"consensusTimestamp\":(\\d{19}),");

        for (String message : inputMessages) {
            Matcher matcher = pattern.matcher(message);
            assertThat(matcher.find())
                    .as("Message is missing consensusTimestamp: " + message)
                    .isTrue();
            messages.put(Long.valueOf(matcher.group(1)), message);
        }

        return messages;
    }
}
