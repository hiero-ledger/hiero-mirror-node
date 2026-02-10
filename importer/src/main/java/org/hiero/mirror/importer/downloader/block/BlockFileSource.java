// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import com.hedera.hapi.block.stream.protoc.Block;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.provider.StreamFileProvider;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
final class BlockFileSource extends AbstractBlockSource {

    private static final String DEFAULT_NODE_ENDPOINT = "cloud";

    private final BlockBucketProperties bucketProperties;
    private final StreamFileProvider streamFileProvider;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final String discoveredNetwork = discoverNetwork();

    // metrics
    private final Timer cloudStorageLatencyMetric;

    BlockFileSource(
            final BlockStreamReader blockStreamReader,
            final BlockStreamVerifier blockStreamVerifier,
            final BlockBucketProperties bucketProperties,
            final CommonDownloaderProperties commonDownloaderProperties,
            final CutoverService cutoverService,
            final MeterRegistry meterRegistry,
            final BlockProperties properties,
            final StreamFileProvider streamFileProvider) {
        super(blockStreamReader, blockStreamVerifier, commonDownloaderProperties, cutoverService, properties);
        this.bucketProperties = bucketProperties;
        this.streamFileProvider = streamFileProvider;

        cloudStorageLatencyMetric = Timer.builder("hiero.mirror.importer.cloud.latency")
                .description("The difference in time between the consensus time of the last transaction in the file "
                        + "and the time at which the file was created in the cloud storage provider")
                .tag("type", StreamType.BLOCK.toString())
                .register(meterRegistry);
    }

    @Override
    @SneakyThrows
    protected void doGet(final long blockNumber) {
        if (blockNumber == EARLIEST_AVAILABLE_BLOCK_NUMBER) {
            throw new IllegalStateException(
                    this.getClass().getSimpleName() + " doesn't support earliest available block number");
        }

        final var network = getDiscoveredNetwork();
        final var path = "%s/%s".formatted(network, StreamType.BLOCK.getPath());
        final var streamFilename = StreamFilename.from(path, blockNumber);

        try {
            final var blockFileData = streamFileProvider
                    .get(streamFilename)
                    .blockOptional(commonDownloaderProperties.getTimeout())
                    .orElseThrow();
            log.debug("Downloaded block file {}", streamFilename.getFilename());

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
        } catch (final Throwable t) {
            throw new BlockStreamException("Failed to download block file " + streamFilename.getFilename(), t);
        }
    }

    private String discoverNetwork() {
        final var importerProperties = commonDownloaderProperties.getImporterProperties();
        final var network = importerProperties.getNetwork();
        if (!bucketProperties.isResettable()) {
            return importerProperties.getNetwork();
        }

        final var prefix = network + "-";
        return streamFileProvider
                .listNetwork()
                .filter(s -> {
                    if (!s.startsWith(prefix)) {
                        return false;
                    }

                    try {
                        Instant.parse(s.substring(prefix.length()));
                        return true;
                    } catch (final Exception e) {
                        return false;
                    }
                })
                .reduce((first, second) -> first.compareTo(second) > 0 ? first : second)
                .doOnNext(latest -> log.info("Discovered latest network folder '{}'", latest))
                .blockOptional()
                .orElseThrow(() ->
                        new IllegalStateException("Failed to discover network folder for '%s'".formatted(network)));
    }

    private BlockStream getBlockStream(final StreamFileData blockFileData) throws IOException {
        try (final var inputStream = blockFileData.getInputStream()) {
            final var block = Block.parseFrom(inputStream);
            final byte[] bytes = blockFileData.getBytes();
            return new BlockStream(
                    block.getItemsList(),
                    bytes,
                    blockFileData.getFilename(),
                    blockFileData.getStreamFilename().getTimestamp());
        }
    }
}
