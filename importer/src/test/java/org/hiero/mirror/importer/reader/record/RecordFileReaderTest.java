// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.importer.TestRecordFiles;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.util.Version;

@ExtendWith(MockitoExtension.class)
abstract class RecordFileReaderTest {

    private static final Collection<RecordFile> ALL_RECORD_FILES =
            TestRecordFiles.getAll().values();

    protected RecordFileReader recordFileReader;
    protected Path testPath;

    @BeforeEach
    void setup() throws Exception {
        testPath = new ClassPathResource("data/recordstreams").getFile().toPath();
        recordFileReader = getRecordFileReader();
    }

    @TestFactory
    Stream<DynamicTest> readValidFile() {
        String template = "read valid version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                recordFile -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                recordFile -> {
                    // given
                    Path testFile = getTestFile(recordFile);
                    StreamFileData streamFileData = StreamFileData.from(testFile.toFile());

                    // when
                    RecordFile actual = recordFileReader.read(streamFileData);

                    // then
                    assertThat(actual)
                            .usingRecursiveComparison()
                            .ignoringFields("bytes", "items", "loadStart", "logsBloomAggregator")
                            .isEqualTo(recordFile);
                    assertThat(actual.getBytes()).isNotEmpty().isEqualTo(streamFileData.getBytes());
                    assertThat(actual.getLoadStart()).isNotNull().isPositive();

                    List<Version> hapiVersions = actual.getItems().stream()
                            .map(RecordItem::getHapiVersion)
                            .toList();
                    assertThat(hapiVersions)
                            .isNotEmpty()
                            .allSatisfy(version -> assertEquals(recordFile.getHapiVersion(), version));

                    List<Long> timestamps = actual.getItems().stream()
                            .map(RecordItem::getConsensusTimestamp)
                            .toList();
                    assertThat(timestamps).first().isEqualTo(recordFile.getConsensusStart());
                    assertThat(timestamps).last().isEqualTo(recordFile.getConsensusEnd());
                    assertThat(timestamps).doesNotHaveDuplicates().isSorted();

                    List<Integer> transactionIndexes = actual.getItems().stream()
                            .map(RecordItem::getTransactionIndex)
                            .toList();
                    assertThat(transactionIndexes).first().isEqualTo(0);
                    assertThat(transactionIndexes)
                            .isEqualTo(IntStream.range(0, recordFile.getCount().intValue())
                                    .boxed()
                                    .toList());
                    assertThat(transactionIndexes).doesNotHaveDuplicates().isSorted();

                    assertThat(actual.getSoftwareVersionMajor()).isEqualTo(actual.getHapiVersionMajor());
                    assertThat(actual.getSoftwareVersionMinor()).isEqualTo(actual.getHapiVersionMinor());
                    assertThat(actual.getSoftwareVersionPatch()).isEqualTo(actual.getHapiVersionPatch());
                });
    }

    @SneakyThrows
    @TestFactory
    Stream<DynamicTest> verifyRecordItemLinksInValidFile() {
        String template = "read file %s containing eth transactions";

        return DynamicTest.stream(
                getFilteredFiles(false),
                recordFile -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                recordFile -> {
                    // given
                    Path testFile = getTestFile(recordFile);
                    StreamFileData streamFileData = StreamFileData.from(testFile.toFile());

                    // when
                    RecordFile actual = recordFileReader.read(streamFileData);

                    // then
                    RecordItem previousItem = null;
                    for (var item : actual.getItems()) {
                        // assert previous link points to previous item
                        assertThat(item.getPrevious()).isEqualTo(previousItem);
                        previousItem = item;
                    }
                });
    }

    @TestFactory
    Stream<DynamicTest> readInvalidFileWithGarbageAppended() {
        String template = "read corrupted version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                recordFile -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                recordFile -> {
                    // given
                    Path testFile = getTestFile(recordFile);
                    byte[] bytes = FileUtils.readFileToByteArray(testFile.toFile());
                    byte[] bytesCorrupted = ArrayUtils.addAll(bytes, new byte[] {0, 1, 2, 3});
                    StreamFileData streamFileData = StreamFileData.from(recordFile.getName(), bytesCorrupted);

                    // when
                    assertThrows(InvalidStreamFileException.class, () -> recordFileReader.read(streamFileData));
                });
    }

    @TestFactory
    Stream<DynamicTest> readInvalidFileWithDataTruncated() {
        String template = "read incomplete version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(false),
                recordFile -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                recordFile -> {
                    // given
                    Path testFile = getTestFile(recordFile);
                    byte[] bytes = FileUtils.readFileToByteArray(testFile.toFile());
                    byte[] bytesTruncated = ArrayUtils.subarray(bytes, 0, bytes.length - 48);
                    StreamFileData streamFileData = StreamFileData.from(recordFile.getName(), bytesTruncated);

                    // when
                    assertThrows(InvalidStreamFileException.class, () -> recordFileReader.read(streamFileData));
                });
    }

    private RecordFile customize(RecordFile expected) {
        if (expected.getVersion() < 6) {
            return expected;
        }

        // RecordFileReaders don't read sidecar files so need to clear some fields to only verify info from
        // sidecar metadata
        var copy = expected.toBuilder().build();
        var sidecars = new ArrayList<SidecarFile>();
        for (var sidecar : expected.getSidecars()) {
            sidecars.add(sidecar.toBuilder()
                    .actualHash(null)
                    .count(null)
                    .size(null)
                    .records(Collections.emptyList())
                    .build());
        }
        copy.setSidecars(sidecars);

        return copy;
    }

    protected Iterator<RecordFile> getFilteredFiles(boolean negate) {
        return ALL_RECORD_FILES.stream()
                .filter(recordFile -> negate ^ filterFile(recordFile.getVersion()))
                .map(this::customize)
                .toList()
                .iterator();
    }

    protected Path getTestFile(RecordFile recordFile) {
        return testPath.resolve("v" + recordFile.getVersion())
                .resolve("record0.0.3")
                .resolve(recordFile.getName());
    }

    protected abstract RecordFileReader getRecordFileReader();

    protected abstract boolean filterFile(int version);
}
