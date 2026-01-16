// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import java.nio.file.Path;
import java.time.Duration;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.FileCopier;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/*
 * These tests exercise the same scenarios using both the legacy node account ID based files for node 0.0.3
 * (via the super classes) and the HIP-679 node ID based bucket structure for node 0.0.4, via test cases defined
 * herein. This tests that S3StreamFileProvider can manage different node types simultaneously.
 *
 * In a manner analogous to S3StreamFileProvider itself, per node information is maintained in nodeInfoMap which
 * is helpful for test scenario setup appropriate for each node path type.
 */
class AutoS3StreamFileProviderTest extends AbstractHip679S3StreamFileProviderTest {

    @Override
    @BeforeEach
    void setup() {
        super.setup();

        properties.setPathType(PathType.AUTO);
        properties.setPathRefreshInterval(Duration.ofSeconds(0L));
    }

    @Override
    protected FileCopier createDefaultFileCopier() {
        return createFileCopier(
                Path.of("data", "hip679", "provider-auto-transition", "recordstreams"), StreamType.RECORD.getPath());
    }

    @Test
    void nodeAccountIdToNodeIdListTransition() {
        var node = node(3);
        var accountIdFileCopier = createDefaultFileCopier();
        accountIdFileCopier.copy();

        // Find files in legacy node account ID bucket structure the first time
        var accountIdData1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var accountIdData2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(accountIdData1)
                .expectNext(accountIdData2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));

        // Consensus node now writes to node ID bucket structure
        var nodeIdFileCopier = createFileCopier(
                Path.of("data", "hip679", "provider-auto-transition", "demo"), importerProperties.getNetwork());
        nodeIdFileCopier.copy();

        // Now find new files in node ID bucket structure for the first time
        var nodeIdData1 = streamFileData(node, "2022-12-25T09_14_26.072307770Z.rcd_sig");
        var nodeIdData2 = streamFileData(node, "2022-12-25T09_14_28.278703292Z.rcd_sig");
        var lastAccountIdFilename = accountIdData2.getStreamFilename();

        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastAccountIdFilename))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(nodeIdData1)
                .expectNext(nodeIdData2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @SuppressWarnings("java:S2699")
    @Test
    void listInvalidFilenameNodeId() {
        var node = node(4);
        listInvalidFilename(createNodeIdFileCopier(), node);
    }

    @SuppressWarnings("java:S2699")
    @Test
    void getNotFoundNodeId() {
        var node = node(4);
        getNotFound(createNodeIdFileCopier(), node);
    }

    @SuppressWarnings("java:S2699")
    @Test
    void getErrorNodeId() {
        var node = node(4);
        getError(createNodeIdFileCopier(), node);
    }

    @SuppressWarnings("java:S2699")
    @Test
    void listNodeId() {
        var node = node(4);
        list(createNodeIdFileCopier(), node);
    }

    @SuppressWarnings("java:S2699")
    @Test
    void listAfterNodeId() {
        var node = node(4);
        listAfter(createNodeIdFileCopier(), node);
    }

    @SuppressWarnings("java:S2699")
    @Test
    void listNotFoundNodeId() {
        var node = node(4);
        listNotFound(createNodeIdFileCopier(), node);
    }

    @SuppressWarnings("java:S2699")
    @Test
    void listErrorNodeId() {
        var node = node(4);
        listError(createNodeIdFileCopier(), node);
    }

    private FileCopier createNodeIdFileCopier() {
        return createFileCopier(Path.of("data", "hip679", "provider-auto", "demo"), importerProperties.getNetwork());
    }
}
