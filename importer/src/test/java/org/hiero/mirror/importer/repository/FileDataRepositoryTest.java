// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;

import com.google.common.primitives.Bytes;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileDataRepositoryTest extends ImporterIntegrationTest {

    @Resource
    private FileDataRepository fileDataRepository;

    private long addressBook101;
    private long addressBook102;

    @BeforeEach
    void setup() {
        addressBook101 = systemEntity.addressBookFile101().getId();
        addressBook102 = systemEntity.addressBookFile102().getId();
    }

    @Test
    void findFilesInRange() {
        List<FileData> fileDataList = new ArrayList<>();
        fileDataList.add(fileData(1, 123, TransactionType.FILECREATE.getProtoId()));
        fileDataList.add(fileData(2, addressBook102, TransactionType.FILECREATE.getProtoId()));
        fileDataList.add(fileData(3, addressBook102, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(4, addressBook102, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(5, addressBook102, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(6, 123, TransactionType.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(7, 123, TransactionType.FILEAPPEND.getProtoId()));
        fileDataRepository.saveAll(fileDataList);

        assertThat(fileDataRepository.findFilesInRange(2, 7, addressBook102, TransactionType.FILEAPPEND.getProtoId()))
                .isNotNull()
                .hasSize(3)
                .extracting(FileData::getConsensusTimestamp)
                .containsSequence(3L, 4L, 5L);
    }

    @Test
    void findFilesOfTransactionTypesInRange() {
        List<FileData> fileDataList = new ArrayList<>();
        fileDataList.add(fileData(1, 123, TransactionType.FILECREATE.getProtoId()));
        FileData fileData = fileData(2, addressBook102, TransactionType.FILEUPDATE.getProtoId());
        fileDataList.add(fileData);
        fileDataList.add(fileData(3, addressBook102, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(4, addressBook102, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(5, addressBook102, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(6, 123, TransactionType.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(7, 123, TransactionType.FILEAPPEND.getProtoId()));
        fileDataRepository.saveAll(fileDataList);

        List<Integer> transactionTypes =
                List.of(TransactionType.FILECREATE.getProtoId(), TransactionType.FILEUPDATE.getProtoId());
        assertThat(fileDataRepository.findLatestMatchingFile(5, addressBook102, transactionTypes))
                .contains(fileData);
    }

    @Test
    void findAddressBookFilesInRange() {
        List<FileData> fileDataList = new ArrayList<>();
        fileDataList.add(fileData(1, addressBook101, TransactionType.FILECREATE.getProtoId()));
        fileDataList.add(fileData(2, addressBook102, TransactionType.FILECREATE.getProtoId()));
        fileDataList.add(fileData(3, addressBook101, TransactionType.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(4, addressBook101, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(5, addressBook102, TransactionType.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(6, addressBook102, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(7, addressBook101, TransactionType.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(8, addressBook102, TransactionType.FILEUPDATE.getProtoId()));
        fileDataRepository.saveAll(fileDataList);

        assertThat(fileDataRepository.findAddressBooksBetween(2, 5, List.of(addressBook101, addressBook102), 10))
                .isNotNull()
                .hasSize(2)
                .extracting(FileData::getConsensusTimestamp)
                .containsSequence(3L, 4L);
    }

    @Test
    void findAddressBookFilesWithLimit() {
        List<FileData> fileDataList = new ArrayList<>();
        fileDataList.add(fileData(1, addressBook101, TransactionType.FILECREATE.getProtoId()));
        fileDataList.add(fileData(2, addressBook102, TransactionType.FILECREATE.getProtoId()));
        fileDataList.add(fileData(3, addressBook101, TransactionType.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(4, addressBook101, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(5, addressBook102, TransactionType.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(6, addressBook102, TransactionType.FILEAPPEND.getProtoId()));
        fileDataList.add(fileData(7, addressBook101, TransactionType.FILEUPDATE.getProtoId()));
        fileDataList.add(fileData(8, addressBook102, TransactionType.FILEUPDATE.getProtoId()));
        fileDataRepository.saveAll(fileDataList);

        assertThat(fileDataRepository.findAddressBooksBetween(2, 10, List.of(addressBook101, addressBook102), 5))
                .isNotNull()
                .hasSize(5)
                .extracting(FileData::getConsensusTimestamp)
                .containsSequence(3L, 4L, 5L, 6L, 7L);
    }

    @Test
    void getFileAtTimestamp() {
        softly.assertThat(fileDataRepository.getFileAtTimestamp(1101, domainBuilder.timestamp()))
                .isEmpty();

        // Create
        var create = domainBuilder.fileData().persist();
        long entityId = create.getEntityId().getId();
        var expected = FileData.builder()
                .consensusTimestamp(create.getConsensusTimestamp())
                .entityId(create.getEntityId())
                .fileData(create.getFileData())
                .transactionType(null)
                .build();
        softly.assertThat(fileDataRepository.getFileAtTimestamp(entityId, create.getConsensusTimestamp()))
                .contains(expected);
        softly.assertThat(fileDataRepository.getFileAtTimestamp(entityId, create.getConsensusTimestamp() - 1))
                .isEmpty();

        // Append
        var append = domainBuilder
                .fileData()
                .customize(
                        f -> f.entityId(create.getEntityId()).transactionType(TransactionType.FILEAPPEND.getProtoId()))
                .persist();
        expected.setFileData(Bytes.concat(create.getFileData(), append.getFileData()));
        softly.assertThat(fileDataRepository.getFileAtTimestamp(entityId, append.getConsensusTimestamp()))
                .contains(expected);

        // Update without changing content
        var update = domainBuilder
                .fileData()
                .customize(f -> f.entityId(create.getEntityId())
                        .fileData(EMPTY_BYTE_ARRAY)
                        .transactionType(TransactionType.FILEUPDATE.getProtoId()))
                .persist();
        softly.assertThat(fileDataRepository.getFileAtTimestamp(entityId, update.getConsensusTimestamp()))
                .contains(expected);

        // Update, change content
        var updateContent = domainBuilder
                .fileData()
                .customize(
                        f -> f.entityId(create.getEntityId()).transactionType(TransactionType.FILEUPDATE.getProtoId()))
                .persist();
        softly.assertThat(fileDataRepository.getFileAtTimestamp(entityId, updateContent.getConsensusTimestamp() - 1))
                .contains(expected);
        expected.setConsensusTimestamp(updateContent.getConsensusTimestamp());
        expected.setFileData(updateContent.getFileData());
        softly.assertThat(fileDataRepository.getFileAtTimestamp(entityId, updateContent.getConsensusTimestamp()))
                .contains(expected);
    }

    private FileData fileData(long consensusTimestamp, long fileId, int transactionType) {
        var fileData = new FileData();
        fileData.setConsensusTimestamp(consensusTimestamp);
        fileData.setFileData("some file data".getBytes());
        fileData.setEntityId(EntityId.of(fileId));
        fileData.setTransactionType(transactionType);
        return fileData;
    }
}
