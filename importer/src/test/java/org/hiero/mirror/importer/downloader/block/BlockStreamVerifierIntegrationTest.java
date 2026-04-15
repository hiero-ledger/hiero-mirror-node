// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.block.stream.protoc.RecordFileSignature;
import com.hedera.hapi.block.stream.protoc.SignedRecordFileProof;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.downloader.NodeSignatureVerifier;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.hiero.mirror.importer.downloader.block.tss.TssVerifier;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.hiero.mirror.importer.exception.HashMismatchException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.hiero.mirror.importer.exception.SignatureVerificationException;
import org.hiero.mirror.importer.reader.block.hash.BlockStateProofHasher;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
final class BlockStreamVerifierIntegrationTest extends ImporterIntegrationTest {

    private final BlockFileTransformer blockFileTransformer;
    private final BlockProperties blockProperties;
    private final BlockStateProofHasher blockStateProofHasher;
    private final ConsensusNodeService consensusNodeService;
    private final LedgerIdPublicationTransactionParser ledgerIdPublicationTransactionParser;
    private final NodeSignatureVerifier nodeSignatureVerifier;
    private final RecordDownloaderProperties recordDownloaderProperties;
    private final RecordFileRepository recordFileRepository;
    private final TssVerifier tssVerifier;

    private BlockStreamVerifier verifier;

    @BeforeEach
    void setup() {
        final var cutoverService =
                new CutoverServiceImpl(blockProperties, recordDownloaderProperties, recordFileRepository);
        verifier = new BlockStreamVerifier(
                blockFileTransformer,
                blockStateProofHasher,
                consensusNodeService,
                cutoverService,
                ledgerIdPublicationTransactionParser,
                new SimpleMeterRegistry(),
                nodeSignatureVerifier,
                cutoverService,
                tssVerifier);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            false, false, true
            true, false, true
            true, true, false
            """)
    void verify(
            final boolean hasPreviousRecordFile,
            final boolean hasPreviousWrappedRecordBlockHash,
            final boolean verifyByConsensusWeight) {
        // given
        final var blockFile = createBlockFileWithWrb();
        if (hasPreviousRecordFile) {
            final var recordFile = blockFile.getRecordFile();
            domainBuilder
                    .recordFile()
                    .customize(r -> {
                        r.hash(recordFile.getPreviousHash()).index(recordFile.getIndex() - 1);
                        if (hasPreviousWrappedRecordBlockHash) {
                            r.wrappedRecordBlockHash(recordFile.getPreviousWrappedRecordBlockHash())
                                    .previousWrappedRecordBlockHash(domainBuilder.bytes(48));
                        }
                    })
                    .persist();
        }

        if (verifyByConsensusWeight) {
            persistNodeStakes();
        }

        // when, then
        assertThatCode(() -> verifier.verify(blockFile)).doesNotThrowAnyException();
    }

    @Test
    void verifyWithHashMismatch() {
        // given
        final var blockFile = createBlockFileWithWrb();
        final var recordFile = blockFile.getRecordFile();
        domainBuilder
                .recordFile()
                .customize(r -> r.index(recordFile.getIndex() - 1))
                .persist();

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(HashMismatchException.class)
                .hasMessageStartingWith("Previous hash mismatch");
    }

    @Test
    void verifyWithWrappedRecordBlockHashMismatch() {
        // given
        final var blockFile = createBlockFileWithWrb();
        final var recordFile = blockFile.getRecordFile();
        domainBuilder
                .recordFile()
                .customize(r -> r.hash(recordFile.getPreviousHash())
                        .index(recordFile.getIndex() - 1)
                        .wrappedRecordBlockHash(domainBuilder.bytes(48)))
                .persist();

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(HashMismatchException.class)
                .hasMessageStartingWith("Previous wrapped record block hash mismatch");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void verifyWithIncorrectSignature(final boolean verifyByConsensusWeight) {
        // given
        final var blockFile = createBlockFileWithWrb();
        final var proofBuilder = blockFile.getBlockProof().getSignedRecordFileProof().toBuilder();
        for (int i = 0; i < 3; i++) {
            proofBuilder.setRecordFileSignatures(
                    i,
                    RecordFileSignature.newBuilder()
                            .setNodeId(i)
                            .setSignaturesBytes(DomainUtils.fromBytes(domainBuilder.bytes(128)))
                            .build());
        }
        blockFile.setBlockProof(blockFile.getBlockProof().toBuilder()
                .setSignedRecordFileProof(proofBuilder.build())
                .build());

        if (verifyByConsensusWeight) {
            persistNodeStakes();
        }

        // when, then
        final var expectedMessage =
                "Consensus not reached for file 2025-06-01T00_00_10.207594022Z.rcd_sig with %s stake"
                        .formatted(verifyByConsensusWeight ? "1000/4000" : "1/4");
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(SignatureVerificationException.class)
                .hasMessage(expectedMessage);
    }

    @Test
    void verifyWithMissingSignatures() {
        // given
        final var blockFile = createBlockFileWithWrb();
        blockFile.setBlockProof(blockFile.getBlockProof().toBuilder()
                .setSignedRecordFileProof(SignedRecordFileProof.newBuilder().setVersion(6))
                .build());

        // when, then
        assertThatThrownBy(() -> verifier.verify(blockFile))
                .isInstanceOf(InvalidStreamFileException.class)
                .hasMessage(
                        "No record file signatures for the wrapped record block 2025-06-01T00_00_10.207594022Z.rcd with block number 20346052");
    }

    private void persistNodeStakes() {
        for (int i = 0; i < 4; i++) {
            final long nodeId = i;
            domainBuilder
                    .nodeStake()
                    .customize(ns -> ns.consensusTimestamp(0L).nodeId(nodeId).stake(1000))
                    .persist();
        }
    }

    @SneakyThrows
    private static BlockFile createBlockFileWithWrb() {
        // The file is the serialized proto bytes with 4 node signatures out of 7 available due to the fact that the
        // addressbook file in test resources only has 4 nodes.
        final var signedRecordFileProof = SignedRecordFileProof.parseFrom(
                FileUtils.readFileToByteArray(TestUtils.getResource("data/signature/wrb/20346052.bin")));
        return BlockFile.builder()
                .blockProof(BlockProof.newBuilder()
                        .setBlock(20346052L)
                        .setSignedRecordFileProof(signedRecordFileProof)
                        .build())
                .index(20346052L)
                .name(BlockFile.getFilename(20346052L, true))
                .node("block-node-1")
                .recordFile(RecordFile.builder()
                        .consensusStart(1748736010207594022L)
                        .consensusEnd(1748736010668768194L)
                        .fileHash(
                                "de900c51272832b0670d204e5d9ff47cd07a5f7c86fac347c41993460ddaec5ee9483c469e6a1c9ed4881ad84201fafa")
                        .hash(
                                "3ba21b30b90c2e9cbae9e55002bc623d27cefeb64832a61a0100fe37f58b7bdc3bc38d1f3ab2d1856ff93d04e604ab99")
                        .index(20346052L)
                        .name("2025-06-01T00_00_10.207594022Z.rcd")
                        .previousHash(
                                "da6ee1fdd0aedd8dd61275daf05441bfb1a4bbc39c65621eb18f0ab9e7aa02a9039f29f21f658a9cea62e18877740c00")
                        .previousWrappedRecordBlockHash(TestUtils.generateRandomByteArray(48))
                        .wrappedRecordBlockHash(TestUtils.generateRandomByteArray(48))
                        .version(6)
                        .build())
                .build();
    }
}
