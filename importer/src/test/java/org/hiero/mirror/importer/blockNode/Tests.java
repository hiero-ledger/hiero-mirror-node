// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.blockNode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;

public class Tests extends BaseClass {

    @Test
    @DisplayName("Test")
    void checkImporterConnectionToBlockNode() throws InterruptedException {
        var network = Network.newNetwork();
        var dbContainer = createDBContainer(network);
        dbContainer.start();
        var blockNodeContainer = createBlockNodeContainer(network);
        blockNodeContainer.start();
        var simulatorContainer = createSimulatorContainer(network);
        simulatorContainer.start();
        var importerContainer = createImporterContainer(network);
        importerContainer.start();
        var blockNodeLogs = blockNodeContainer.getLogs();
        assertThat(blockNodeLogs).contains("Starting BlockNode Server");
        assertThat(waitForLogMessage(importerContainer, "Start streaming block 0 from BlockNode", 30, 1000))
                .isTrue();
        var importerLogs = importerContainer.getLogs();
        assertThat(importerLogs).contains("Start streaming block 0 from BlockNode");
    }
}
