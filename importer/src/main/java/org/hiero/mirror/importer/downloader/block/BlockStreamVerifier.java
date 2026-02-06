// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FilenameUtils;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.hiero.mirror.importer.downloader.block.tss.TssVerifier;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
final class BlockStreamVerifier {

    private final BlockFileTransformer blockFileTransformer;
    private final CutoverService cutoverService;
    private final LedgerIdPublicationTransactionParser ledgerIdPublicationTransactionParser;
    private final StreamFileNotifier streamFileNotifier;
    private final TssVerifier tssVerifier;

    private final MeterProvider<Timer> streamVerificationMeterProvider;
    private final MeterProvider<Timer> streamLatencyMeterProvider;
    private final Timer streamCloseMetric;

    public BlockStreamVerifier(
            final BlockFileTransformer blockFileTransformer,
            final CutoverService cutoverService,
            final LedgerIdPublicationTransactionParser ledgerIdPublicationTransactionParser,
            final MeterRegistry meterRegistry,
            final StreamFileNotifier streamFileNotifier,
            final TssVerifier tssVerifier) {
        this.blockFileTransformer = blockFileTransformer;
        this.cutoverService = cutoverService;
        this.ledgerIdPublicationTransactionParser = ledgerIdPublicationTransactionParser;
        this.streamFileNotifier = streamFileNotifier;
        this.tssVerifier = tssVerifier;

        // Metrics
        this.streamVerificationMeterProvider = Timer.builder("hiero.mirror.importer.stream.verification")
                .description("The duration in seconds it took to verify consensus and hash chain of a stream file")
                .tag("type", StreamType.BLOCK.toString())
                .withRegistry(meterRegistry);

        this.streamLatencyMeterProvider = Timer.builder("hiero.mirror.importer.stream.latency")
                .description("The difference in time between the consensus time of the last transaction in the block "
                        + "and the time at which the block was verified")
                .tag("type", StreamType.BLOCK.toString())
                .withRegistry(meterRegistry);

        streamCloseMetric = Timer.builder("hiero.mirror.importer.stream.close.latency")
                .description("The difference between the consensus start of the current and the last stream file")
                .tag("type", StreamType.BLOCK.toString())
                .register(meterRegistry);
    }

    public void verify(BlockFile blockFile) {
        final var startTime = Instant.now();
        boolean success = true;
        try {
            verifyBlockNumber(blockFile);
            verifyHashChain(blockFile);
            verifyTssSignature(blockFile);

            final var consensusEnd = Instant.ofEpochSecond(0, blockFile.getConsensusEnd());
            streamLatencyMeterProvider
                    .withTag("block_node", blockFile.getNode())
                    .record(Duration.between(consensusEnd, Instant.now()));

            final var lastRecordFile = cutoverService.getLastRecordFile();
            final var recordFile = blockFileTransformer.transform(blockFile);
            streamFileNotifier.verified(recordFile);

            lastRecordFile.map(RecordFile::getConsensusStart).ifPresent(lastConsensusStart -> {
                final long latency = blockFile.getConsensusStart() - lastConsensusStart;
                streamCloseMetric.record(latency, TimeUnit.NANOSECONDS);
            });
        } catch (Exception e) {
            success = false;
            throw e;
        } finally {
            streamVerificationMeterProvider
                    .withTags("success", String.valueOf(success), "block_node", blockFile.getNode())
                    .record(Duration.between(startTime, Instant.now()));
        }
    }

    private Optional<String> getExpectedPreviousHash() {
        return cutoverService.getLastRecordFile().map(RecordFile::getHash);
    }

    private void updateLedger(final BlockFile blockFile) {
        final var transaction = blockFile.getLastLedgerIdPublicationTransaction();
        if (blockFile.getIndex() != 0 || transaction == null) {
            return;
        }

        final var ledger = ledgerIdPublicationTransactionParser.parse(
                transaction.getConsensusTimestamp(),
                transaction.getTransactionBody().getLedgerIdPublication());
        tssVerifier.setLedger(ledger);
    }

    private void verifyBlockNumber(final BlockFile blockFile) {
        final var blockNumber = blockFile.getIndex();
        cutoverService.getLastRecordFile().map(RecordFile::getIndex).ifPresent(lastBlockNumber -> {
            if (blockNumber != lastBlockNumber + 1) {
                throw new InvalidStreamFileException(String.format(
                        "Non-consecutive block number, previous = %d, current = %d", lastBlockNumber, blockNumber));
            }
        });

        try {
            final var filename = blockFile.getName();
            final int endIndex = filename.indexOf(FilenameUtils.EXTENSION_SEPARATOR);
            final long actual = Long.parseLong(endIndex != -1 ? filename.substring(0, endIndex) : filename);
            if (actual != blockNumber) {
                throw new InvalidStreamFileException(String.format(
                        "Block number mismatch, from filename = %d, from content = %d", actual, blockNumber));
            }
        } catch (final NumberFormatException e) {
            throw new InvalidStreamFileException("Failed to parse block number from filename " + blockFile.getName());
        }
    }

    private void verifyHashChain(final BlockFile blockFile) {
        getExpectedPreviousHash().ifPresent(expected -> {
            if (!blockFile.getPreviousHash().contentEquals(expected)) {
                throw new HashMismatchException(blockFile.getName(), expected, blockFile.getPreviousHash(), "Previous");
            }
        });
    }

    private void verifyTssSignature(final BlockFile blockFile) {
        updateLedger(blockFile);
        // TBA tssVerifier.verify
    }
}
