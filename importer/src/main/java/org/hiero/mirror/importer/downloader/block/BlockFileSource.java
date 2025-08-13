// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import com.google.common.base.Stopwatch;
import com.hedera.hapi.block.stream.protoc.Block;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.downloader.provider.TransientProviderException;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.util.Utility;

@Named
final class BlockFileSource extends AbstractBlockSource {

    private final ConsensusNodeService consensusNodeService;
    private final StreamFileProvider streamFileProvider;

    // metrics
    private final Timer cloudStorageLatencyMetric;
    private final Timer downloadLatencyMetric;

    BlockFileSource(
            BlockStreamReader blockStreamReader,
            BlockStreamVerifier blockStreamVerifier,
            CommonDownloaderProperties commonDownloaderProperties,
            ConsensusNodeService consensusNodeService,
            MeterRegistry meterRegistry,
            BlockProperties properties,
            StreamFileProvider streamFileProvider) {
        super(blockStreamReader, blockStreamVerifier, commonDownloaderProperties, properties);
        this.consensusNodeService = consensusNodeService;
        this.streamFileProvider = streamFileProvider;

        cloudStorageLatencyMetric = Timer.builder("hiero.mirror.importer.cloud.latency")
                .description("The difference in time between the consensus time of the last transaction in the file "
                        + "and the time at which the file was created in the cloud storage provider")
                .tag("type", StreamType.BLOCK.toString())
                .register(meterRegistry);

        downloadLatencyMetric = Timer.builder("hiero.mirror.importer.stream.latency")
                .description("The difference in time between the consensus time of the last transaction in the file "
                        + "and the time at which the file was downloaded and verified")
                .tag("type", StreamType.BLOCK.toString())
                .register(meterRegistry);
    }

    @Override
    protected void doGet(long blockNumber, Long endBlockNumber) {
        var nodes = getRandomizedNodes();
        var stopwatch = Stopwatch.createStarted();
        var streamFilename = StreamFilename.from(blockNumber);
        var filename = streamFilename.getFilename();
        var streamPath = commonDownloaderProperties.getImporterProperties().getStreamPath();
        var timeout = commonDownloaderProperties.getTimeout();

        for (int i = 0; i < nodes.size() && timeout.isPositive(); i++) {
            var node = nodes.get(i);
            long nodeId = node.getNodeId();

            try {
                var blockFileData = streamFileProvider
                        .get(node, streamFilename)
                        .blockOptional(timeout)
                        .orElseThrow();
                log.debug("Downloaded block file {} from node {}", filename, nodeId);

                var blockStream = getBlockStream(blockFileData, nodeId);
                var blockFile = onBlockStream(blockStream);

                var cloudStorageTime = blockFileData.getLastModified();
                var consensusEnd = Instant.ofEpochSecond(0, blockFile.getConsensusEnd());
                cloudStorageLatencyMetric.record(Duration.between(consensusEnd, cloudStorageTime));
                downloadLatencyMetric.record(Duration.between(consensusEnd, Instant.now()));

                if (properties.isWriteFiles()) {
                    Utility.archiveFile(blockFileData.getFilePath(), blockStream.bytes(), streamPath);
                }

                return;
            } catch (TransientProviderException e) {
                log.warn(
                        "Trying next node after failing to download block file {} from node {}: {}",
                        filename,
                        nodeId,
                        e.getMessage());
            } catch (Throwable t) {
                log.error("Failed to process block file {} from node {}", filename, nodeId, t);
            }

            timeout = commonDownloaderProperties.getTimeout().minus(stopwatch.elapsed());
        }

        throw new BlockStreamException("Failed to download block file " + filename);
    }

    private BlockStream getBlockStream(StreamFileData blockFileData, long nodeId) throws IOException {
        try (var inputStream = blockFileData.getInputStream()) {
            var block = Block.parseFrom(inputStream);
            byte[] bytes = blockFileData.getBytes();
            return new BlockStream(
                    block.getItemsList(),
                    System.currentTimeMillis(),
                    bytes,
                    blockFileData.getFilename(),
                    blockFileData.getStreamFilename().getTimestamp(),
                    nodeId);
        }
    }

    private List<ConsensusNode> getRandomizedNodes() {
        var nodes = new ArrayList<>(consensusNodeService.getNodes());
        Collections.shuffle(nodes);
        return nodes;
    }
}
