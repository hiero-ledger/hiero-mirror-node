// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

abstract class AbstractRecordFileReaderTest extends RecordFileReaderTest {

    @TestFactory
    Stream<DynamicTest> readIncompatibleFile() {
        String template = "read incompatible version %d file %s";

        return DynamicTest.stream(
                getFilteredFiles(true),
                recordFile -> String.format(template, recordFile.getVersion(), recordFile.getName()),
                recordFile -> {
                    // given
                    Path testFile = getTestFile(recordFile);
                    StreamFileData streamFileData = StreamFileData.from(testFile.toFile());

                    // when
                    assertThrows(InvalidStreamFileException.class, () -> recordFileReader.read(streamFileData));
                });
    }
}
