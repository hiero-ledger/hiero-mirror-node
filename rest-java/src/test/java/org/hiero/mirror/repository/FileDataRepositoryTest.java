// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILEAPPEND;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILECREATE;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILEUPDATE;

import com.google.common.primitives.Bytes;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.RestJavaIntegrationTest;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.restjava.repository.FileDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class FileDataRepositoryTest extends RestJavaIntegrationTest {

    /** Guaranteed not to match persisted {@link #entityId} rows in file_data. */
    private static final long NONEXISTENT_ENTITY_ID = 9_876_543_210L;

    private final FileDataRepository fileDataRepository;
    private EntityId entityId;

    @BeforeEach
    void setup() {
        entityId = domainBuilder.entityId();
    }

    @Test
    void getFileAtTimestamp() {
        // given
        final var fileData1 = fileData(FILECREATE, 100);
        final var fileData2 = fileData(FILEAPPEND, 10);
        final var fileData3 = fileData(FILEUPDATE, 0);
        final var fileData4 = fileData(FILEAPPEND, 10);
        final var fileData5 = fileData(FILEUPDATE, 50);
        final var fileData6 = fileData(FILEAPPEND, 10);

        // when / then
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
        // given
        fileData(FILECREATE, 100);

        // when
        final var actual = fileDataRepository.getFileAtTimestamp(NONEXISTENT_ENTITY_ID, 0L, Long.MAX_VALUE);

        // then — aggregate queries can yield one NULL-filled row instead of zero rows
        assertThat(actual.flatMap(fd -> Optional.ofNullable(fd.getConsensusTimestamp())))
                .isEmpty();
    }

    @Test
    void getLatestTimestampEmpty() {
        assertThat(fileDataRepository.getLatestTimestamp(entityId.getId())).isEmpty();
    }

    @Test
    void getLatestTimestampWrongEntity() {
        // given
        fileData(FILECREATE, 100);

        // when
        final var actual = fileDataRepository.getLatestTimestamp(NONEXISTENT_ENTITY_ID);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void getLatestTimestampExcludesAppendAndEmptyUpdate() {
        // given — FILEAPPEND (type 16) and empty FILEUPDATE are both excluded by the filter
        fileData(FILEAPPEND, 10);
        fileData(FILEUPDATE, 0);

        // when
        final var actual = fileDataRepository.getLatestTimestamp(entityId.getId());

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void getLatestTimestamp() {
        // given
        fileData(FILECREATE, 100);
        fileData(FILEAPPEND, 10); // excluded (type 16)
        fileData(FILEUPDATE, 0); // excluded (empty data)
        final var latestIncluded = fileData(FILEUPDATE, 50);

        // when
        final var actual = fileDataRepository.getLatestTimestamp(entityId.getId());

        // then
        assertThat(actual).hasValue(latestIncluded.getConsensusTimestamp());
    }

    private void assertFileData(long lower, long upper, FileData... fileData) {
        if (fileData.length > 0) {
            var bytes = Bytes.concat(Arrays.stream(fileData)
                    .map(FileData::getFileData)
                    .filter(ArrayUtils::isNotEmpty)
                    .toArray(i -> new byte[i][]));
            var expected = fileData[fileData.length - 1].toBuilder()
                    .fileData(bytes)
                    .transactionType(null)
                    .build();
            assertThat(fileDataRepository.getFileAtTimestamp(entityId.getId(), lower, upper))
                    .get()
                    .isEqualTo(expected);
        } else {
            final var opt = fileDataRepository.getFileAtTimestamp(entityId.getId(), lower, upper);
            assertThat(opt.flatMap(fd -> Optional.ofNullable(fd.getConsensusTimestamp())))
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
