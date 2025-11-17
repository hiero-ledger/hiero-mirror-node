// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.FilenameUtils;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.util.Version;
import org.web3j.utils.Strings;

@Named
@NullMarked
final class BlockStreamVerifier {

    static final BlockFile EMPTY = BlockFile.builder().build();

    private static final String EMPTY_HASH = Strings.repeat('0', 96);

    private final BlockFileTransformer blockFileTransformer;
    private final BlockProperties blockProperties;
    private final RecordFileRepository recordFileRepository;
    private final StreamFileNotifier streamFileNotifier;

    // Metrics
    private final MeterRegistry meterRegistry;
    private final Timer.Builder streamVerificationMetric;
    private final Timer streamCloseMetric;

    private final AtomicReference<Optional<BlockFile>> lastBlockFile = new AtomicReference<>(Optional.empty());

    public BlockStreamVerifier(
            BlockFileTransformer blockFileTransformer,
            BlockProperties blockProperties,
            RecordFileRepository recordFileRepository,
            StreamFileNotifier streamFileNotifier,
            MeterRegistry meterRegistry) {
        this.blockFileTransformer = blockFileTransformer;
        this.blockProperties = blockProperties;
        this.recordFileRepository = recordFileRepository;
        this.streamFileNotifier = streamFileNotifier;
        this.meterRegistry = meterRegistry;

        this.streamVerificationMetric = Timer.builder("hiero.mirror.importer.stream.verification")
                .description("The duration in seconds it took to verify consensus and hash chain of a stream file")
                .tag("type", StreamType.BLOCK.toString());

        streamCloseMetric = Timer.builder("hiero.mirror.importer.stream.close.latency")
                .description("The difference between the consensus start of the current and the last stream file")
                .tag("type", StreamType.BLOCK.toString())
                .register(meterRegistry);
    }

    public Optional<BlockFile> getLastBlockFile() {
        return Objects.requireNonNull(lastBlockFile.get()).or(() -> {
            var last = recordFileRepository
                    .findLatest()
                    .map(r -> BlockFile.builder()
                            .consensusStart(r.getConsensusStart())
                            .hash(r.getHash())
                            .index(r.getIndex())
                            .name(r.getName())
                            .build())
                    .or(() -> Optional.of(EMPTY));
            lastBlockFile.compareAndSet(Optional.empty(), last);
            return last;
        });
    }

    public void verify(BlockFile blockFile) {
        var startTime = Instant.now();
        boolean success = true;
        try {
            verifyBlockNumber(blockFile);
            verifyHashChain(blockFile);
            var recordFile = blockFileTransformer.transform(blockFile);
            streamFileNotifier.verified(recordFile);

            getLastBlockFile().ifPresent(last -> {
                if (!last.equals(EMPTY)) {
                    long latency = blockFile.getConsensusStart() - last.getConsensusStart();
                    streamCloseMetric.record(latency, TimeUnit.NANOSECONDS);
                }
            });

            setLastBlockFile(blockFile);
        } catch (Exception e) {
            success = false;
            throw e;
        } finally {
            streamVerificationMetric
                    .tag("success", String.valueOf(success))
                    .register(meterRegistry)
                    .record(Duration.between(startTime, Instant.now()));
        }
    }

    private Optional<String> getExpectedPreviousHash() {
        return getLastBlockFile().map(BlockFile::getHash);
    }

    private void setLastBlockFile(BlockFile blockFile) {
        var copy = (BlockFile) blockFile.copy();
        copy.clear();
        lastBlockFile.set(Optional.of(copy));
    }

    private void verifyBlockNumber(BlockFile blockFile) {
        var blockNumber = blockFile.getIndex();
        getLastBlockFile().map(BlockFile::getIndex).ifPresent(lastBlockNumber -> {
            if (blockNumber != lastBlockNumber + 1) {
                throw new InvalidStreamFileException(String.format(
                        "Non-consecutive block number, previous = %d, current = %d", lastBlockNumber, blockNumber));
            }
        });

        try {
            String filename = blockFile.getName();
            int endIndex = filename.indexOf(FilenameUtils.EXTENSION_SEPARATOR);
            long actual = Long.parseLong(endIndex != -1 ? filename.substring(0, endIndex) : filename);
            if (actual != blockNumber) {
                throw new InvalidStreamFileException(String.format(
                        "Block number mismatch, from filename = %d, from content = %d", actual, blockNumber));
            }
        } catch (NumberFormatException e) {
            throw new InvalidStreamFileException("Failed to parse block number from filename " + blockFile.getName());
        }
    }

    private void verifyHashChain(BlockFile blockFile) {
        final var consensusNodeVersion = blockFile.getBlockHeader().getSoftwareVersion();
        final var version = new Version(
                consensusNodeVersion.getMajor(), consensusNodeVersion.getMinor(), consensusNodeVersion.getPatch());
        if (version.isGreaterThanOrEqualTo(blockProperties.getNewRootHashAlgorithmVersion())) {
            // Set both hash and previousHash to all 0s to pass parser validation
            blockFile.setHash(EMPTY_HASH);
            blockFile.setPreviousHash(EMPTY_HASH);
            return;
        }

        getExpectedPreviousHash().ifPresent(expected -> {
            if (!blockFile.getPreviousHash().contentEquals(expected)) {
                throw new HashMismatchException(blockFile.getName(), expected, blockFile.getPreviousHash(), "Previous");
            }
        });
    }
}
