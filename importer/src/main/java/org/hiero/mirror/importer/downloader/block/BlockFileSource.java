// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.hiero.mirror.common.util.DomainUtils.parseProtobuf;
import static org.hiero.mirror.importer.downloader.block.scheduler.Scheduler.EARLIEST_AVAILABLE_BLOCK_NUMBER;

import com.google.common.base.Stopwatch;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import org.hiero.block.internal.protoc.BlockUnparsed;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.downloader.provider.TransientProviderException;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
final class BlockFileSource extends AbstractBlockSource {

    static final String CONSENSUS_NODE_FOLDER = "consensus-node";

    private static final String DEFAULT_NODE_ENDPOINT = "cloud";

    private final ConsensusNodeService consensusNodeService;
    private final StreamFileProvider streamFileProvider;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final String discoveredNetwork = discoverNetwork();

    // metrics
    private final Timer cloudStorageLatencyMetric;
    private final Counter failoverMetric;

    BlockFileSource(
            final BlockStreamReader blockStreamReader,
            final BlockStreamVerifier blockStreamVerifier,
            final CommonDownloaderProperties commonDownloaderProperties,
            final ConsensusNodeService consensusNodeService,
            final CutoverService cutoverService,
            final MeterRegistry meterRegistry,
            final BlockProperties properties,
            final StreamFileProvider streamFileProvider) {
        super(blockStreamReader, blockStreamVerifier, commonDownloaderProperties, cutoverService, properties);
        this.consensusNodeService = consensusNodeService;
        this.streamFileProvider = streamFileProvider;

        cloudStorageLatencyMetric = Timer.builder("hiero.mirror.importer.cloud.latency")
                .description("The difference in time between the consensus time of the last transaction in the file "
                        + "and the time at which the file was created in the cloud storage provider")
                .tag("type", StreamType.BLOCK.toString())
                .register(meterRegistry);
        failoverMetric = BlockSource.failoverMetric(meterRegistry, FailoverType.CONSENSUS_NODE);
    }

    /**
     * Downloads the block from the canonical HIP-1193 path, falling back to the per consensus node paths in random
     * order when it can't be downloaded, parsed or verified. All attempts share one download timeout.
     */
    @Override
    protected void doGet(final long blockNumber, final Long endBlockNumber) {
        if (blockNumber == EARLIEST_AVAILABLE_BLOCK_NUMBER) {
            throw new IllegalStateException(
                    this.getClass().getSimpleName() + " doesn't support earliest available block number");
        }

        final var filename = BlockFile.getFilename(blockNumber, true);
        final var paths = getPaths(getDiscoveredNetwork());
        final var stopwatch = Stopwatch.createStarted();
        Throwable failure = null;

        for (int i = 0; i < paths.size(); i++) {
            final var path = paths.get(i);
            final var timeout = commonDownloaderProperties.getTimeout().minus(stopwatch.elapsed());
            if (!timeout.isPositive()) {
                break;
            }

            // Every path after the canonical one is a per consensus node fallback path. Only record failover once
            // for all attempts in the same round
            if (i == 1) {
                failoverMetric.increment();
            }

            try {
                download(StreamFilename.from(path, blockNumber), timeout);
                return;
            } catch (final TransientProviderException e) {
                failure = e;
                log.debug("Block file {} not found in {}: {}", filename, path, e.getMessage());
            } catch (final Throwable t) {
                failure = t;
                log.warn("Failed to get block file {} from {}", filename, path, t);
            }
        }

        throw new BlockStreamException("Failed to download block file " + filename, failure);
    }

    private String discoverNetwork() {
        final var network = commonDownloaderProperties.getImporterProperties().getNetwork();
        return streamFileProvider
                .discoverNetwork()
                .doOnNext(n -> log.info("Discovered latest network folder '{}'", n))
                .blockOptional()
                .orElseThrow(() ->
                        new IllegalStateException("Failed to discover network folder for '%s'".formatted(network)));
    }

    private void download(final StreamFilename streamFilename, final Duration timeout) throws IOException {
        final var blockFileData =
                streamFileProvider.get(streamFilename).blockOptional(timeout).orElseThrow();
        log.debug("Downloaded block file {} from {}", streamFilename.getFilename(), streamFilename.getPath());

        final var blockStream = getBlockStream(blockFileData);
        final var blockFile = onBlockStream(blockStream, DEFAULT_NODE_ENDPOINT);

        final var cloudStorageTime = blockFileData.getLastModified();
        final var consensusEnd = Instant.ofEpochSecond(0, blockFile.getConsensusEnd());
        cloudStorageLatencyMetric.record(Duration.between(consensusEnd, cloudStorageTime));

        if (properties.isWriteFiles()) {
            final var streamPath =
                    commonDownloaderProperties.getImporterProperties().getStreamPath();
            Utility.archiveFile(blockFileData.getFilePath(), blockStream.bytes(), streamPath);
        }
    }

    private BlockStream getBlockStream(final StreamFileData blockFileData) throws IOException {
        try (final var inputStream = blockFileData.getInputStream()) {
            final int maxSize = (int) commonDownloaderProperties.getMaxSize().toBytes();
            final var block = parseProtobuf(inputStream, BlockUnparsed::parseFrom, maxSize);
            final byte[] bytes = blockFileData.getBytes();
            return new BlockStream(
                    block.getBlockItemsList(),
                    blockFileData.getStreamFilename().getTimestamp(),
                    bytes,
                    blockFileData.getFilename(),
                    bytes.length);
        }
    }

    // The canonical path first, followed by the per consensus node paths in random order
    private List<String> getPaths(final String network) {
        final var nodes = consensusNodeService.getNodes();
        final var paths = new ArrayList<String>(nodes.size() + 1);
        for (final var node : nodes) {
            paths.add("%s/%s/%d".formatted(network, CONSENSUS_NODE_FOLDER, node.getNodeId()));
        }

        Collections.shuffle(paths);
        paths.addFirst("%s/%s".formatted(network, StreamType.BLOCK.getPath()));
        return paths;
    }
}
