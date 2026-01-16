// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_FILE_DATA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.record.RecordFileParser;
import org.hiero.mirror.importer.parser.record.RecordItemListener;
import org.hiero.mirror.importer.parser.record.RecordParserProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@RequiredArgsConstructor
final class FileDataServiceTest extends ImporterIntegrationTest {

    @Qualifier(CACHE_FILE_DATA)
    private final CacheManager cacheManager;

    @MockitoSpyBean
    private final RecordItemListener recordItemListener;

    private final RecordFileParser recordFileParser;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordParserProperties recordParserProperties;
    private final FileDataService service;

    @AfterEach
    void teardown() {
        recordFileParser.clear();
    }

    @Test
    void cacheEvicted() {
        // given, the cache max size is set to 2 in test application.yml
        var file1Append = recordItemBuilder.fileAppend().build();
        var file2Append = recordItemBuilder.fileAppend().build();
        var file3Append = recordItemBuilder.fileAppend().build();
        long consensusTimestamp = file1Append.getConsensusTimestamp();
        var fileId1 =
                EntityId.of(file1Append.getTransactionBody().getFileAppend().getFileID());
        var fileId2 =
                EntityId.of(file2Append.getTransactionBody().getFileAppend().getFileID());
        var fileId3 =
                EntityId.of(file3Append.getTransactionBody().getFileAppend().getFileID());
        var file1ExistingData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(consensusTimestamp - 3).entityId(fileId1))
                .persist();
        var file2ExistingData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(consensusTimestamp - 2).entityId(fileId2))
                .persist();
        var file3ExistingData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(consensusTimestamp - 1).entityId(fileId3))
                .persist();
        Map<EntityId, List<byte[]>> inBandFileData = new HashMap<>();
        var fileIds = List.of(fileId1, fileId2, fileId3);
        doAnswer(invocation -> {
                    var result = invocation.callRealMethod();
                    fileIds.forEach(id -> inBandFileData
                            .computeIfAbsent(id, k -> new ArrayList<>())
                            .add(service.get(id)));
                    return result;
                })
                .when(recordItemListener)
                .onItem(any(RecordItem.class));
        byte[] file1CompleteData = combine(file1ExistingData.getFileData(), combine(file1Append));
        byte[] file2CompleteData = combine(file2ExistingData.getFileData(), combine(file2Append));
        byte[] file3CompleteData = combine(file3ExistingData.getFileData(), combine(file3Append));
        var expected = Map.of(
                fileId1, Collections.nCopies(3, file1CompleteData),
                fileId2, List.of(file2ExistingData.getFileData(), file2CompleteData, file2CompleteData),
                fileId3, List.of(file3ExistingData.getFileData(), file3ExistingData.getFileData(), file3CompleteData));

        // when
        parseRecordItems(null, List.of(file1Append, file2Append, file3Append));

        // then
        assertThat(inBandFileData)
                // otherwise comparing List<byte[]> will fail
                .usingRecursiveComparison()
                .isEqualTo(expected);
        assertThat(service.get(fileId1)).isEqualTo(file1CompleteData);
        assertThat(service.get(fileId2)).isEqualTo(file2CompleteData);
        assertThat(service.get(fileId3)).isEqualTo(file3CompleteData);
    }

    @Test
    void empty() {
        assertThat(service.get(domainBuilder.entityId())).isNull();
        assertThat(service.get(null)).isNull();
        assertThat(service.get(EntityId.EMPTY)).isNull();
    }

    @Test
    void getFromDb() {
        // given
        var cryptoTransfer = recordItemBuilder.cryptoTransfer().build();
        var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(cryptoTransfer.getConsensusTimestamp() - 1))
                .persist();
        var fileId = fileData.getEntityId();
        var inBandFileData = new AtomicReference<byte[]>();
        doAnswer(invocation -> {
                    var result = invocation.callRealMethod();
                    inBandFileData.set(service.get(fileId));
                    return result;
                })
                .when(recordItemListener)
                .onItem(any(RecordItem.class));

        // when
        parseRecordItems(null, List.of(cryptoTransfer));

        // then
        byte[] expected = fileData.getFileData();
        assertThat(inBandFileData.get()).isEqualTo(expected);
        assertThat(service.get(fileId)).isEqualTo(expected);
    }

    @Test
    void idempotency() {
        // given
        var file1Append = recordItemBuilder.fileAppend().build();
        var fileId1 =
                EntityId.of(file1Append.getTransactionBody().getFileAppend().getFileID());
        var existing = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(file1Append.getConsensusTimestamp() - 1)
                        .entityId(fileId1))
                .persist();
        var file2Create = recordItemBuilder.fileCreate().build();
        var fileId2 =
                EntityId.of(file2Create.getTransactionRecord().getReceipt().getFileID());
        var file2Append = recordItemBuilder
                .fileAppend()
                .transactionBody(b -> b.setFileID(fileId2.toFileID()))
                .build();
        var file3Append = recordItemBuilder.fileAppend().build();
        var fileId3 =
                EntityId.of(file3Append.getTransactionBody().getFileAppend().getFileID());
        var fileIds = List.of(fileId1, fileId2, fileId3);
        Map<EntityId, List<byte[]>> inBandFileData = new HashMap<>();
        doAnswer(invocation -> {
                    var result = invocation.callRealMethod();
                    fileIds.forEach(id -> inBandFileData
                            .computeIfAbsent(id, k -> new ArrayList<>())
                            .add(service.get(id)));

                    if (invocation.getArgument(0, RecordItem.class).getConsensusTimestamp()
                            == file3Append.getConsensusTimestamp()) {
                        throw new RuntimeException("oops");
                    }
                    return result;
                })
                .when(recordItemListener)
                .onItem(any(RecordItem.class));
        var expectedCompleteFile1Content = combine(existing.getFileData(), combine(file1Append));
        var expectedInBandFileData = Map.of(
                fileId1, Collections.nCopies(4, expectedCompleteFile1Content),
                fileId2,
                        Lists.newArrayList(
                                null,
                                combine(file2Create),
                                combine(file2Create, file2Append),
                                combine(file2Create, file2Append)),
                fileId3, Lists.<byte[]>newArrayList(null, null, null, null));

        // when
        var recordItems = List.of(file1Append, file2Create, file2Append, file3Append);
        parseRecordItems(null, recordItems);

        // then
        assertThat(inBandFileData)
                // otherwise comparing List<byte[]> will fail
                .usingRecursiveComparison()
                .isEqualTo(withRetry(expectedInBandFileData));
        assertThat(service.get(fileId1)).isEqualTo(existing.getFileData());
        assertThat(service.get(fileId2)).isNull();
        assertThat(service.get(fileId3)).isNull();

        // when: process again, without exception
        inBandFileData.clear();
        doAnswer(invocation -> {
                    var result = invocation.callRealMethod();
                    fileIds.forEach(id -> inBandFileData
                            .computeIfAbsent(id, k -> new ArrayList<>())
                            .add(service.get(id)));
                    return result;
                })
                .when(recordItemListener)
                .onItem(any(RecordItem.class));
        parseRecordItems(null, recordItems);

        // then
        assertThat(inBandFileData)
                // otherwise comparing List<byte[]> will fail
                .usingRecursiveComparison()
                .isEqualTo(expectedInBandFileData);
        assertThat(service.get(fileId1)).isEqualTo(combine(existing.getFileData(), combine(file1Append)));
        assertThat(service.get(fileId2)).isEqualTo(combine(file2Create, file2Append));
        assertThat(service.get(fileId3)).isNull();
    }

    @Test
    void missingFirstChunk() {
        // given
        var fileAppend = recordItemBuilder.fileAppend().build();
        var fileId = EntityId.of(fileAppend.getTransactionBody().getFileAppend().getFileID());
        var inBandFileData = new AtomicReference<byte[]>();
        doAnswer(invocation -> {
                    var result = invocation.callRealMethod();
                    inBandFileData.set(service.get(fileId));
                    return result;
                })
                .when(recordItemListener)
                .onItem(any(RecordItem.class));

        // when
        parseRecordItems(null, List.of(fileAppend));

        // then
        assertThat(inBandFileData.get()).isNull();
        assertThat(service.get(fileId)).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void multipleLifecycles(boolean resetCache) {
        // given
        var fileAppend = recordItemBuilder.fileAppend().build();
        var fileId = EntityId.of(fileAppend.getTransactionBody().getFileAppend().getFileID());
        var existing = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(fileAppend.getConsensusTimestamp() - 1)
                        .entityId(fileId))
                .persist();
        var fileUpdateNoContents = recordItemBuilder
                .fileUpdate()
                .transactionBody(b -> b.setFileID(fileId.toFileID()).clearContents())
                .build();
        var fileUpdate = recordItemBuilder
                .fileUpdate()
                .transactionBody(b -> b.setFileID(fileId.toFileID()))
                .build();
        var inBandFileData = new ArrayList<byte[]>();
        doAnswer(invocation -> {
                    var result = invocation.callRealMethod();
                    inBandFileData.add(service.get(fileId));
                    return result;
                })
                .when(recordItemListener)
                .onItem(any(RecordItem.class));

        // when
        var recordFile = parseRecordItems(null, List.of(fileAppend, fileUpdateNoContents, fileUpdate));

        // then
        var expected = List.of(
                combine(existing.getFileData(), combine(fileAppend)),
                combine(existing.getFileData(), combine(fileAppend)),
                combine(fileUpdate));
        assertThat(inBandFileData).usingRecursiveComparison().isEqualTo(expected);
        assertThat(service.get(fileId)).isEqualTo(expected.getLast());

        // when
        inBandFileData.clear();
        if (resetCache) {
            resetCacheManager(cacheManager);
        }
        var fileAppend2 = recordItemBuilder
                .fileAppend()
                .transactionBody(b -> b.setFileID(fileId.toFileID()))
                .build();
        var fileUpdate2 = recordItemBuilder
                .fileUpdate()
                .transactionBody(b -> b.setFileID(fileId.toFileID()))
                .build();
        var fileUpdate3 = recordItemBuilder
                .fileUpdate()
                .transactionBody(b -> b.setFileID(fileId.toFileID()))
                .build();
        parseRecordItems(recordFile, List.of(fileAppend2, fileUpdate2, fileUpdate3));

        // then
        expected = List.of(combine(fileUpdate, fileAppend2), combine(fileUpdate2), combine(fileUpdate3));
        assertThat(inBandFileData).usingRecursiveComparison().isEqualTo(expected);
        assertThat(service.get(fileId)).isEqualTo(expected.getLast());
    }

    @Test
    void systemFile() {
        // given
        var fileId = domainBuilder.entityNum(920);
        var systemFileUpdate = recordItemBuilder
                .fileUpdate()
                .transactionBody(b -> b.setFileID(fileId.toFileID()))
                .build();
        domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(systemFileUpdate.getConsensusTimestamp() - 1)
                        .entityId(fileId))
                .persist();
        var inBandFileData = new AtomicReference<byte[]>();
        doAnswer(invocation -> {
                    var result = invocation.callRealMethod();
                    inBandFileData.set(service.get(fileId));
                    return result;
                })
                .when(recordItemListener)
                .onItem(any(RecordItem.class));

        // when
        parseRecordItems(null, List.of(systemFileUpdate));

        // then
        assertThat(inBandFileData.get()).isNull();
        assertThat(service.get(fileId)).isNull();
    }

    private RecordFile parseRecordItems(RecordFile last, List<RecordItem> recordItems) {
        long index = last != null ? last.getIndex() + 1 : domainBuilder.id();
        long consensusStart = recordItems.getFirst().getConsensusTimestamp();
        var name = StreamFilename.getFilename(
                StreamType.RECORD, StreamFilename.FileType.DATA, Instant.ofEpochSecond(0, consensusStart));
        var previousHash = last != null ? last.getHash() : domainBuilder.hash(96);
        var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.consensusEnd(recordItems.getLast().getConsensusTimestamp())
                        .consensusStart(consensusStart)
                        .index(index)
                        .items(recordItems)
                        .name(name)
                        .previousHash(previousHash))
                .get();

        try {
            recordFileParser.parse(recordFile);
        } catch (Exception e) {
            // ignore
        }

        return recordFile;
    }

    private Map<EntityId, List<byte[]>> withRetry(Map<EntityId, List<byte[]>> expected) {
        int maxAttempts = recordParserProperties.getRetry().getMaxAttempts();
        if (maxAttempts <= 1) {
            return expected;
        }

        var result = new HashMap<EntityId, List<byte[]>>();
        for (var entityId : expected.keySet()) {
            var repeated = new ArrayList<byte[]>();
            for (int i = 0; i < maxAttempts; i++) {
                repeated.addAll(expected.get(entityId));
            }
            result.put(entityId, repeated);
        }

        return result;
    }

    private static byte[] combine(byte[] first, byte[] remaining) {
        return Bytes.concat(first, remaining);
    }

    private static byte[] combine(RecordItem... fileRecordItems) {
        return Arrays.stream(fileRecordItems)
                .map(RecordItem::getTransactionBody)
                .map(b -> switch (b.getDataCase()) {
                    case FILEAPPEND -> b.getFileAppend().getContents();
                    case FILECREATE -> b.getFileCreate().getContents();
                    case FILEUPDATE -> b.getFileUpdate().getContents();
                    default -> throw new IllegalArgumentException("Incorrect transaction data case");
                })
                .map(DomainUtils::toBytes)
                .reduce(Bytes::concat)
                .orElseThrow();
    }
}
