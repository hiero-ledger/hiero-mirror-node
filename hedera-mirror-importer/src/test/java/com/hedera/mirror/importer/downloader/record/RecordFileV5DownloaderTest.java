// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.record;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.TestRecordFiles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

class RecordFileV5DownloaderTest extends AbstractRecordFileDownloaderTest {

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        Map<String, RecordFile> allRecordFileMap = TestRecordFiles.getAll();
        RecordFile recordFile1 = allRecordFileMap.get("2021-01-11T22_09_24.063739000Z.rcd");
        RecordFile recordFile2 = allRecordFileMap.get("2021-01-11T22_09_34.097416003Z.rcd");
        return Map.of(recordFile1.getName(), recordFile1, recordFile2.getName(), recordFile2);
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v5");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(10L);
    }
}
