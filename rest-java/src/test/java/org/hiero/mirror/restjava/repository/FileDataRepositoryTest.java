// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILEAPPEND;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILECREATE;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILEUPDATE;

import com.google.common.primitives.Bytes;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class FileDataRepositoryTest extends RestJavaIntegrationTest {

    private final FileDataRepository fileDataRepository;
    private EntityId entityId;

    @BeforeEach
    void setup() {
        entityId = domainBuilder.entityId();
    }

    @Test
    void getFileAtTimestamp() {
        // Given
        final var fileData1 = fileData(FILECREATE, 100);
        final var fileData2 = fileData(FILEAPPEND, 10);
        final var fileData3 = fileData(FILEUPDATE, 0);
        final var fileData4 = fileData(FILEAPPEND, 10);
        final var fileData5 = fileData(FILEUPDATE, 50);
        final var fileData6 = fileData(FILEAPPEND, 10);

        // When / Then
        assertFileData(0L, fileData1.getConsensusTimestamp(), fileData1);
        assertFileData(0L, fileData2.getConsensusTimestamp(), fileData1, fileData2);
        assertFileData(0L, fileData3.getConsensusTimestamp(), fileData1, fileData2);
        assertFileData(0L, fileData4.getConsensusTimestamp(), fileData1, fileData2, fileData4);
        assertFileData(0L, fileData5.getConsensusTimestamp(), fileData5);
        assertFileData(0L, fileData6.getConsensusTimestamp(), fileData5, fileData6);
        assertFileData(0L, fileData2.getConsensusTimestamp() - 1, fileData1);
        assertFileData(0L, fileData3.getConsensusTimestamp() - 1, fileData1, fileData2);
        assertFileData(0L, fileData4.getConsensusTimestamp() - 1, fileData1, fileData2);
        assertFileData(0L, fileData5.getConsensusTimestamp() - 1, fileData1, fileData2, fileData4);
        assertFileData(0L, fileData6.getConsensusTimestamp() - 1, fileData5);
        assertFileData(0L, fileData6.getConsensusTimestamp() + 1, fileData5, fileData6);
        assertFileData(fileData1.getConsensusTimestamp(), fileData1.getConsensusTimestamp(), fileData1);
        assertFileData(fileData1.getConsensusTimestamp() + 1, fileData4.getConsensusTimestamp());
        assertFileData(fileData6.getConsensusTimestamp(), fileData6.getConsensusTimestamp());
    }

    @Test
    void getFileAtTimestampWrongEntity() {
        // Given
        fileData(FILECREATE, 100);
        final var wrongId = domainBuilder.entityId().getId();

        // When
        final var actual = fileDataRepository.getFileAtTimestamp(wrongId, 0L, Long.MAX_VALUE);

        // Then
        assertThat(actual).isEmpty();
    }

    private void assertFileData(long lower, long upper, FileData... fileData) {
        if (fileData.length > 0) {
            var bytes = Bytes.concat(Arrays.stream(fileData)
                    .map(FileData::getFileData)
                    .filter(ArrayUtils::isNotEmpty)
                    .toArray(i -> new byte[i][]));
            var expected = fileData[0].toBuilder()
                    .fileData(bytes)
                    .transactionType(null)
                    .build();
            assertThat(fileDataRepository.getFileAtTimestamp(entityId.getId(), lower, upper))
                    .get()
                    .isEqualTo(expected);
        } else {
            assertThat(fileDataRepository.getFileAtTimestamp(entityId.getId(), lower, upper))
                    .isEmpty();
        }
    }

    private FileData fileData(TransactionType type, int byteLength) {
        final var bytes = byteLength == 0 ? null : domainBuilder.bytes(byteLength);
        return domainBuilder
                .fileData()
                .customize(f -> f.fileData(bytes).entityId(entityId).transactionType(type.getProtoId()))
                .persist();
    }
}
