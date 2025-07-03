// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.record;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.FileCopier;
import org.hiero.mirror.importer.TestRecordFiles;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordFileV2DownloaderTest extends AbstractRecordFileDownloaderTest {

    @Override
    protected Map<String, RecordFile> getRecordFileMap() {
        Map<String, RecordFile> allRecordFileMap = TestRecordFiles.getAll();
        RecordFile recordFile1 = allRecordFileMap.get("2019-08-30T18_10_00.419072Z.rcd");
        RecordFile recordFile2 = allRecordFileMap.get("2019-08-30T18_10_05.249678Z.rcd");
        return Map.of(recordFile1.getName(), recordFile1, recordFile2.getName(), recordFile2);
    }

    @Override
    protected Path getTestDataDir() {
        return Paths.get("recordstreams", "v2");
    }

    @Override
    protected Duration getCloseInterval() {
        return Duration.ofSeconds(5L);
    }

    @Test
    @DisplayName("Download and verify V1 files")
    void downloadV1() {
        loadAddressBook("test-v1");
        var allRecordFiles = TestRecordFiles.getAll();
        var testRecordFiles = Map.of(
                "2019-07-01T14_13_00.317763Z.rcd", allRecordFiles.get("2019-07-01T14_13_00.317763Z.rcd"),
                "2019-07-01T14_29_00.302068Z.rcd", allRecordFiles.get("2019-07-01T14_29_00.302068Z.rcd"));
        setupRecordFiles(testRecordFiles);

        fileCopier = FileCopier.create(TestUtils.getResource("data").toPath(), s3Path)
                .from(downloaderProperties.getStreamType().getPath(), "v1")
                .to(
                        commonDownloaderProperties.getBucketName(),
                        downloaderProperties.getStreamType().getPath());
        fileCopier.copy();
        expectLastStreamFile(Instant.EPOCH);

        downloader.download();

        verifyForSuccess();
    }
}
