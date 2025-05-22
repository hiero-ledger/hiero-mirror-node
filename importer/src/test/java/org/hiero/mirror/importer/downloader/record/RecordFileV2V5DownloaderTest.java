// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.record;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.TestRecordFiles;

// test v2 record file followed by a v5 record file, the start object running hash in v5 record file should match the
// file hash of the last v2 record file
class RecordFileV2V5DownloaderTest extends AbstractRecordFileDownloaderTest {

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        List<RecordFile> recordFiles = TestRecordFiles.getV2V5Files();
        RecordFile recordFileV2 = recordFiles.get(0);
        RecordFile recordFileV5 = recordFiles.get(1);
        return Map.of(recordFileV2.getName(), recordFileV2, recordFileV5.getName(), recordFileV5);
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(232L);
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v2v5");
    }
}
