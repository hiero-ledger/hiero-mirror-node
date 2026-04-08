// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.hedera.hapi.block.stream.protoc.BlockProof;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;
import org.apache.commons.io.FilenameUtils;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.domain.StreamFileSignature;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.NodeSignatureVerifier;
import org.hiero.mirror.importer.downloader.StreamFileNotifier;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.hiero.mirror.importer.downloader.block.tss.TssVerifier;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.reader.block.hash.BlockStateProofHasher;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;

@CustomLog
@Named
@NullMarked
final class BlockStreamVerifier {

    private final BlockFileTransformer blockFileTransformer;
    private final BlockStateProofHasher blockStateProofHasher;
    private final ConsensusNodeService consensusNodeService;
    private final CutoverService cutoverService;
    private final LedgerIdPublicationTransactionParser ledgerIdPublicationTransactionParser;
    private final NodeSignatureVerifier nodeSignatureVerifier;
    private final StreamFileNotifier streamFileNotifier;
    private final TssVerifier tssVerifier;

    private final MeterProvider<Timer> streamVerificationMeterProvider;
    private final MeterProvider<Timer> streamLatencyMeterProvider;
    private final Timer streamCloseMetric;

    private boolean logTssSignatureSize = true;

    public BlockStreamVerifier(
            final BlockFileTransformer blockFileTransformer,
            final BlockStateProofHasher blockStateProofHasher,
            final ConsensusNodeService consensusNodeService,
            final CutoverService cutoverService,
            final LedgerIdPublicationTransactionParser ledgerIdPublicationTransactionParser,
            final MeterRegistry meterRegistry,
            final NodeSignatureVerifier nodeSignatureVerifier,
            final StreamFileNotifier streamFileNotifier,
            final TssVerifier tssVerifier) {
        this.blockFileTransformer = blockFileTransformer;
        this.blockStateProofHasher = blockStateProofHasher;
        this.consensusNodeService = consensusNodeService;
        this.cutoverService = cutoverService;
        this.ledgerIdPublicationTransactionParser = ledgerIdPublicationTransactionParser;
        this.nodeSignatureVerifier = nodeSignatureVerifier;
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
            verifySignature(blockFile);

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

    private byte[] getRootHash(final long blockNumber, final BlockProof blockProof, final byte[] hash) {
        if (blockProof.hasSignedBlockProof()) {
            return hash;
        }

        final var stateProof = blockProof.getBlockStateProof();
        return blockStateProofHasher.getRootHash(blockNumber, hash, stateProof.getPathsList());
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
        } catch (final NumberFormatException _) {
            throw new InvalidStreamFileException("Failed to parse block number from filename " + blockFile.getName());
        }
    }

    private void verifyHashChain(final BlockFile blockFile) {
        cutoverService.getLastRecordFile().ifPresent(lastRecordFile -> {
            final boolean isLastRecordFile = lastRecordFile.getVersion() < BlockStreamReader.VERSION;
            final var previousHash = lastRecordFile.getHash();

            if (!isLastRecordFile) {
                if (!blockFile.getPreviousHash().contentEquals(previousHash)) {
                    throw new HashMismatchException(
                            blockFile.getName(), previousHash, blockFile.getPreviousHash(), "Previous");
                }

                return;
            }

            // The last verified file is a record file
            if (blockFile.hasRecordFile()) {
                // This is a wrapped record block, verify both
                // - record file hash chain
                // - conditionally wrapped record block hash chain if the last one is also a wrapped record block
                final var recordFile = blockFile.getRecordFile();
                if (!recordFile.getPreviousHash().contentEquals(previousHash)) {
                    throw new HashMismatchException(
                            recordFile.getName(), previousHash, recordFile.getPreviousHash(), "Previous");
                }

                final byte[] previousWrappedRecordBlockHash = lastRecordFile.getWrappedRecordBlockHash();
                if (previousWrappedRecordBlockHash != null
                        && Arrays.equals(
                                recordFile.getPreviousWrappedRecordBlockHash(), previousWrappedRecordBlockHash)) {
                    throw new HashMismatchException(
                            recordFile.getName(),
                            previousWrappedRecordBlockHash,
                            recordFile.getPreviousWrappedRecordBlockHash(),
                            "Previous Wrapped Record Block");
                }
            } else {
                // First block after cutover
                blockFile.setPreviousWrappedRecordBlockHash(Hex.decode(blockFile.getPreviousHash()));
                blockFile.setPreviousHash(previousHash);
            }
        });
    }

    private void verifySignature(final BlockFile blockFile) {
        if (!blockFile.hasRecordFile()) {
            verifyTssSignature(blockFile);
        } else {
            verifyWrappedRecordBlockSignature(blockFile);
        }
    }

    private void verifyTssSignature(final BlockFile blockFile) {
        updateLedger(blockFile);

        final var blockProof = blockFile.getBlockProof();
        if (!blockProof.hasSignedBlockProof() && !blockProof.hasBlockStateProof()) {
            throw new InvalidStreamFileException("Invalid block proof case " + blockProof.getProofCase());
        }

        final byte[] hash = getRootHash(blockFile.getIndex(), blockProof, blockFile.getRawHash());
        final var tssSignedBlockProof = blockProof.hasSignedBlockProof()
                ? blockProof.getSignedBlockProof()
                : blockProof.getBlockStateProof().getSignedBlockProof();
        final byte[] signature = toBytes(tssSignedBlockProof.getBlockSignature());

        if (logTssSignatureSize) {
            log.info(
                    "{} TSS signature size for block {} is {}",
                    blockProof.hasSignedBlockProof() ? "BlockProof" : "StateProof",
                    blockFile.getIndex(),
                    signature.length);
            logTssSignatureSize = false;
        }

        tssVerifier.verify(blockFile.getIndex(), hash, signature);
    }

    private void verifyWrappedRecordBlockSignature(final BlockFile blockFile) {
        final var recordFileSignatures =
                blockFile.getBlockProof().getSignedRecordFileProof().getRecordFileSignaturesList();
        final var recordFile = blockFile.getRecordFile();
        if (recordFileSignatures.isEmpty()) {
            throw new InvalidStreamFileException(
                    "No record file signatures for the wrapped record block %s with block number %d"
                            .formatted(recordFile.getName(), recordFile.getIndex()));
        }

        final var fileHash = Hex.decode(recordFile.getFileHash());
        final var filename = StreamFilename.from("%s%s".formatted(recordFile.getName(), StreamType.SIGNATURE_SUFFIX));
        final var signatures = new ArrayList<StreamFileSignature>(recordFileSignatures.size());
        for (final var recordFileSignature : recordFileSignatures) {
            final var node = consensusNodeService.getNode(recordFileSignature.getNodeId());
            if (node == null) {
                Utility.handleRecoverableError(
                        "No consensus node exists for node id {} in SignedRecordFileProof",
                        recordFileSignature.getNodeId());
                continue;
            }

            signatures.add(StreamFileSignature.builder()
                    .fileHash(fileHash)
                    .fileHashSignature(DomainUtils.toBytes(recordFileSignature.getSignaturesBytes()))
                    .filename(filename)
                    .node(node)
                    .signatureType(StreamFileSignature.SignatureType.SHA_384_WITH_RSA)
                    .version((byte) BlockStreamReader.VERSION)
                    .build());
        }

        nodeSignatureVerifier.verify(signatures);
    }
}
