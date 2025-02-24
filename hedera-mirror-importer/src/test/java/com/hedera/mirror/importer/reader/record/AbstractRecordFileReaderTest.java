// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.record;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import java.nio.file.Path;
import java.util.stream.Stream;
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
