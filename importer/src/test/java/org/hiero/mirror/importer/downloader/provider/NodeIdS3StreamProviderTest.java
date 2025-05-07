// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import java.nio.file.Path;
import java.time.Duration;
import org.hiero.mirror.importer.FileCopier;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import org.junit.jupiter.api.BeforeEach;

/*
 * Copy HIP-679 bucket structured files and configure the stream file provider to access S3 using
 * the new consensus node ID based hierarchy rather than the legacy node account ID mechanism.
 */
class NodeIdS3StreamProviderTest extends AbstractHip679S3StreamFileProviderTest {

    @Override
    @BeforeEach
    void setup() {
        super.setup();

        properties.setPathType(PathType.NODE_ID);
        properties.setPathRefreshInterval(Duration.ofSeconds(2L));
    }

    @Override
    protected FileCopier createDefaultFileCopier() {
        return createFileCopier(Path.of("data", "hip679", "provider-node-id"), importerProperties.getNetwork());
    }
}
