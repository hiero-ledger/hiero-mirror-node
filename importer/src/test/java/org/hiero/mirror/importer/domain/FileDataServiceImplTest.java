// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.Bytes;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.config.CacheProperties;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class FileDataServiceImplTest extends ImporterIntegrationTest {

    private final CacheProperties cacheProperties;
    private final FileDataRepository fileDataRepository;

    private FileDataServiceImpl service;

    @BeforeEach
    void setup() {
        service = new FileDataServiceImpl(cacheProperties, fileDataRepository);
    }

    @Test
    void empty() {
        assertThat(service.get(domainBuilder.timestamp(), domainBuilder.entityId()))
                .isNull();
    }

    @Test
    void getFromDb() {
        // given
        var fileData = domainBuilder.fileData().persist();
        long consensusTimestamp = fileData.getConsensusTimestamp();
        var fileId = fileData.getEntityId();

        // when, then
        byte[] expected = fileData.getFileData();
        assertThat(service.get(consensusTimestamp, fileId)).isEqualTo(expected);
        assertThat(service.get(consensusTimestamp + 1, fileId)).isEqualTo(expected);
        assertThat(service.get(consensusTimestamp - 1, fileId)).isNull();
    }

    @Test
    void idempotency() {
        // given
        var file1Data1 = domainBuilder
                .fileData()
                .customize(b -> b.transactionType(TransactionType.FILEUPDATE.getProtoId()))
                .persist();
        var fileId1 = file1Data1.getEntityId();
        var fileId2 = domainBuilder.entityId();

        // when, then
        var recordFile = domainBuilder.recordFile().get();
        service.onStart(recordFile);
        var file1Data2 = domainBuilder
                .fileData()
                .customize(b -> b.entityId(fileId1).transactionType(TransactionType.FILEAPPEND.getProtoId()))
                .get();
        var file2Data =
                domainBuilder.fileData().customize(b -> b.entityId(fileId2)).get();

        // query before processing any FileData
        long consensusStart = recordFile.getConsensusStart();
        assertThat(service.get(consensusStart, fileId1)).isEqualTo(file1Data1.getFileData());
        assertThat(service.get(consensusStart, fileId2)).isNull();

        // file1 appended
        service.onFileData(file1Data2);
        byte[] combined = Bytes.concat(file1Data1.getFileData(), file1Data2.getFileData());
        assertThat(service.get(file1Data2.getConsensusTimestamp(), fileId1)).isEqualTo(combined);

        // file2 created
        service.onFileData(file2Data);
        assertThat(service.get(file2Data.getConsensusTimestamp(), fileId2)).isEqualTo(file2Data.getFileData());

        // when record file is processed again, then
        service.onStart(recordFile);
        assertThat(service.get(file1Data2.getConsensusTimestamp(), fileId1)).isEqualTo(file1Data1.getFileData());
        assertThat(service.get(consensusStart, fileId2)).isNull();

        // process fileData
        List.of(file1Data2, file2Data).forEach(service::onFileData);

        // then
        long consensusTimestamp = file2Data.getConsensusTimestamp();
        assertThat(service.get(consensusTimestamp, fileId1)).isEqualTo(combined);
        assertThat(service.get(consensusTimestamp, fileId2)).isEqualTo(file2Data.getFileData());
    }

    @Test
    void multipleLifecyclesInMemory() {
        // given
        var fileData1 = domainBuilder.fileData().get();
        var fileId = fileData1.getEntityId();
        // update without content change
        var fileData2 = domainBuilder
                .fileData()
                .customize(b -> b.entityId(fileId)
                        .fileData(ArrayUtils.EMPTY_BYTE_ARRAY)
                        .transactionType(TransactionType.FILEUPDATE.getProtoId()))
                .get();
        var fileData3 = domainBuilder
                .fileData()
                .customize(b -> b.entityId(fileId).transactionType(TransactionType.FILEAPPEND.getProtoId()))
                .get();
        // update with content change
        var fileData4 = domainBuilder
                .fileData()
                .customize(b -> b.entityId(fileId).transactionType(TransactionType.FILEUPDATE.getProtoId()))
                .get();
        var fileData5 = domainBuilder
                .fileData()
                .customize(b -> b.entityId(fileId).transactionType(TransactionType.FILEAPPEND.getProtoId()))
                .get();

        // when, then
        service.onFileData(fileData1);
        assertThat(service.get(fileData1.getConsensusTimestamp(), fileId)).isEqualTo(fileData1.getFileData());

        service.onFileData(fileData2);
        assertThat(service.get(fileData2.getConsensusTimestamp(), fileId)).isEqualTo(fileData1.getFileData());

        service.onFileData(fileData3);
        assertThat(service.get(fileData3.getConsensusTimestamp(), fileId))
                .isEqualTo(Bytes.concat(fileData1.getFileData(), fileData3.getFileData()));

        service.onFileData(fileData4);
        assertThat(service.get(fileData4.getConsensusTimestamp(), fileId)).isEqualTo(fileData4.getFileData());

        service.onFileData(fileData5);
        assertThat(service.get(fileData5.getConsensusTimestamp(), fileId))
                .isEqualTo(Bytes.concat(fileData4.getFileData(), fileData5.getFileData()));
    }

    @Test
    void systemFileData() {
        var fileId = domainBuilder.entityNum(999L);
        domainBuilder.fileData().customize(b -> b.entityId(fileId)).persist();
        var fileUpdate = domainBuilder
                .fileData()
                .customize(b -> b.entityId(fileId).transactionType(TransactionType.FILEAPPEND.getProtoId()))
                .get();
        service.onFileData(fileUpdate);
        assertThat(service.get(fileUpdate.getConsensusTimestamp(), fileId)).isNull();
    }
}
