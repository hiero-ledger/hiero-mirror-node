// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.record;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.TestRecordFiles;
import org.junit.jupiter.api.BeforeEach;

class RecordFileV5V6DownloaderTest extends AbstractRecordFileDownloaderTest {

    private static final RecordFile recordFileV5 =
            TestRecordFiles.getV5V6Files().get(0);
    private static final RecordFile recordFileV6 =
            TestRecordFiles.getV5V6Files().get(1);

    @BeforeEach
    void setup() {
        loadAddressBook("test-v6-4n.bin");
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v5v6");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(64L);
    }

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        return Map.of(recordFileV5.getName(), recordFileV5, recordFileV6.getName(), recordFileV6);
    }

    @Override
    protected Map<String, Long> getExpectedFileIndexMap() {
        return Map.of(recordFileV6.getName(), recordFileV6.getIndex());
    }
}
