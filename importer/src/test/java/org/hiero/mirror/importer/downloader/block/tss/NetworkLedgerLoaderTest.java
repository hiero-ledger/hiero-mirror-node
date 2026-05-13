// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.input.protoc.EventHeader;
import com.hedera.hapi.block.stream.input.protoc.RoundHeader;
import com.hedera.hapi.block.stream.output.protoc.BlockFooter;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import com.hedera.hapi.block.stream.protoc.BlockProof;
import com.hedera.hapi.node.tss.legacy.LedgerIdNodeContribution;
import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.parser.record.sidecar.SidecarProperties;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReaderImpl;
import org.hiero.mirror.importer.reader.block.record.CompositeRecordFileItemReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;

@ExtendWith(MockitoExtension.class)
final class NetworkLedgerLoaderTest {

    private static final Path BLOCK0_FIXTURE_PATH = Path.of("src/test/resources/networkledger/000.blk.zstd");

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    @TempDir
    private Path tempDir;

    @Mock
    private ResourceLoader resourceLoader;

    private ImporterProperties importerProperties;
    private BlockProperties blockProperties;
    private NetworkLedgerLoader loader;

    private static LedgerIdPublicationTransactionBody buildProtoBody() {
        return LedgerIdPublicationTransactionBody.newBuilder()
                .setHistoryProofVerificationKey(ByteString.copyFrom(new byte[64]))
                .setLedgerId(ByteString.copyFrom(new byte[32]))
                .addNodeContributions(LedgerIdNodeContribution.newBuilder()
                        .setHistoryProofKey(ByteString.copyFrom(new byte[64]))
                        .setNodeId(1L)
                        .setWeight(100L)
                        .build())
                .build();
    }

    @BeforeEach
    void setup() {
        importerProperties = new ImporterProperties();
        blockProperties = new BlockProperties(importerProperties);
        loader = new NetworkLedgerLoader(
                blockProperties, importerProperties, new LedgerIdPublicationTransactionParser(), resourceLoader);
    }

    @Test
    void loadWhenLedgerAlreadyConfigured() throws IOException {
        var existingLedger = LedgerProperties.builder()
                .historyProofVerificationKey(new byte[] {1, 2, 3})
                .ledgerId(new byte[] {4, 5, 6})
                .nodeContributions(List.of())
                .build();
        blockProperties.setLedger(existingLedger);
        var path = tempDir.resolve("ledger");
        Files.write(path, buildProtoBody().toByteArray());
        blockProperties.setInitialLedgerIdPublication(path);

        loader.load();

        assertThat(blockProperties.getLedger()).isSameAs(existingLedger);
    }

    @Test
    void loadFromInitialLedgerIdPublication() throws IOException {
        var body = buildProtoBody();
        var path = tempDir.resolve("ledger");
        Files.write(path, body.toByteArray());
        blockProperties.setInitialLedgerIdPublication(path);

        loader.load();

        assertLedgerMatches(body);
    }

    @Test
    void loadFromInitialLedgerIdPublicationMalformed() throws IOException {
        var path = tempDir.resolve("ledger");
        Files.write(path, new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        blockProperties.setInitialLedgerIdPublication(path);

        assertThatThrownBy(loader::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse initialLedgerIdPublication file");
    }

    @Test
    void loadFromClasspathForMainnet() {
        importerProperties.setNetwork(HederaNetwork.MAINNET);
        var body = buildProtoBody();
        when(resourceLoader.getResource("classpath:/networkledger/" + HederaNetwork.MAINNET))
                .thenReturn(new ByteArrayResource(body.toByteArray()));

        loader.load();

        assertLedgerMatches(body);
    }

    @Test
    void loadFromClasspathForTestnet() {
        importerProperties.setNetwork(HederaNetwork.TESTNET);
        var body = buildProtoBody();
        when(resourceLoader.getResource("classpath:/networkledger/" + HederaNetwork.TESTNET))
                .thenReturn(new ByteArrayResource(body.toByteArray()));

        loader.load();

        assertLedgerMatches(body);
    }

    @Test
    void skipClasspathLoadForUnbundledNetwork() {
        importerProperties.setNetwork(HederaNetwork.PREVIEWNET);

        loader.load();

        assertThat(blockProperties.getLedger()).isNull();
    }

    @Test
    void skipClasspathLoadForDemoNetwork() {
        importerProperties.setNetwork(HederaNetwork.DEMO);

        loader.load();

        assertThat(blockProperties.getLedger()).isNull();
    }

    @Test
    void skipWhenBundledClasspathResourceMissing() {
        importerProperties.setNetwork(HederaNetwork.MAINNET);
        when(resourceLoader.getResource("classpath:/networkledger/" + HederaNetwork.MAINNET))
                .thenReturn(new ClassPathResource("nonexistent-network-ledger"));

        loader.load();

        assertThat(blockProperties.getLedger()).isNull();
    }

    @Test
    void loadFromClasspathMalformed() {
        importerProperties.setNetwork(HederaNetwork.MAINNET);
        when(resourceLoader.getResource("classpath:/networkledger/" + HederaNetwork.MAINNET))
                .thenReturn(new ByteArrayResource(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));

        assertThatThrownBy(loader::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse bundled network ledger");
    }

    /**
     * Reads the committed src/test/resources/networkledger/000.blk.zstd fixture, extracts the
     * LedgerIdPublicationTransactionBody via BlockStreamReader, and drives the loader through the
     * classpath path. The fixture is generated by {@link #generateBlock0Fixture()} (disabled — run
     * manually to regenerate).
     */
    @Test
    void loadFromBlock0Fixture() throws IOException {
        var blockResource = new ClassPathResource("networkledger/000.blk.zstd");
        assumeTrue(blockResource.exists(), "networkledger/000.blk.zstd not present; skipping");

        var filename = BlockFile.getFilename(0, true);
        var zstdBytes = blockResource.getContentAsByteArray();
        var streamFileData = StreamFileData.from(filename, zstdBytes);
        final Block block;
        try (var in = streamFileData.getInputStream()) {
            block = Block.parseFrom(in);
        }
        var blockStream = new BlockStream(
                block.getItemsList(),
                zstdBytes,
                filename,
                streamFileData.getStreamFilename().getTimestamp());

        var blockReader = new BlockStreamReaderImpl(new CompositeRecordFileItemReader(new SidecarProperties()));
        var blockFile = blockReader.read(blockStream);
        var lastLedgerIdPublication = blockFile.getLastLedgerIdPublicationTransaction();
        assertThat(lastLedgerIdPublication)
                .as("block 0 fixture should contain a LedgerIdPublicationTransaction")
                .isNotNull();
        var bodyBytes = lastLedgerIdPublication
                .getTransactionBody()
                .getLedgerIdPublication()
                .toByteArray();

        importerProperties.setNetwork(HederaNetwork.MAINNET);
        when(resourceLoader.getResource("classpath:/networkledger/" + HederaNetwork.MAINNET))
                .thenReturn(new ByteArrayResource(bodyBytes));

        loader.load();

        var ledger = blockProperties.getLedger();
        assertThat(ledger).isNotNull();
        assertThat(ledger.getLedgerId()).isNotEmpty();
        assertThat(ledger.getHistoryProofVerificationKey()).isNotEmpty();
        assertThat(ledger.getNodeContributions()).isNotEmpty();
    }

    /**
     * Generates a synthetic block 0 containing a LedgerIdPublicationTransaction and writes it to
     * src/test/resources/networkledger/000.blk.zstd. Run manually to (re)generate the fixture.
     */
    @Test
    @Disabled("Run manually to regenerate src/test/resources/networkledger/000.blk.zstd")
    void generateBlock0Fixture() throws IOException {
        var ledgerBody = recordItemBuilder
                .ledgerIdPublication()
                .build()
                .getTransactionBody()
                .getLedgerIdPublication();
        var transactionBody =
                TransactionBody.newBuilder().setLedgerIdPublication(ledgerBody).build();
        var consensusTimestamp = recordItemBuilder.timestamp();

        var block = Block.newBuilder()
                .addItems(blockHeader())
                .addItems(roundHeader())
                .addItems(eventHeader())
                .addItems(signedTransaction(transactionBody))
                .addItems(transactionResult(TransactionResult.newBuilder()
                        .setConsensusTimestamp(consensusTimestamp)
                        .setStatus(ResponseCodeEnum.SUCCESS)
                        .build()))
                .addItems(blockFooter())
                .addItems(blockProof())
                .build();
        var zstdBytes = TestUtils.zstd(block.toByteArray());

        Files.createDirectories(BLOCK0_FIXTURE_PATH.getParent());
        Files.write(BLOCK0_FIXTURE_PATH, zstdBytes);
    }

    private BlockItem blockHeader() {
        return BlockItem.newBuilder()
                .setBlockHeader(BlockHeader.newBuilder().setBlockTimestamp(recordItemBuilder.timestamp()))
                .build();
    }

    private static BlockItem blockFooter() {
        return BlockItem.newBuilder()
                .setBlockFooter(BlockFooter.getDefaultInstance())
                .build();
    }

    private static BlockItem blockProof() {
        return BlockItem.newBuilder()
                .setBlockProof(BlockProof.getDefaultInstance())
                .build();
    }

    private static BlockItem eventHeader() {
        return BlockItem.newBuilder()
                .setEventHeader(EventHeader.getDefaultInstance())
                .build();
    }

    private static BlockItem roundHeader() {
        return BlockItem.newBuilder()
                .setRoundHeader(RoundHeader.getDefaultInstance())
                .build();
    }

    private static BlockItem signedTransaction(TransactionBody transactionBody) {
        var signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .build();
        return BlockItem.newBuilder()
                .setSignedTransaction(signedTransaction.toByteString())
                .build();
    }

    private static BlockItem transactionResult(TransactionResult transactionResult) {
        return BlockItem.newBuilder().setTransactionResult(transactionResult).build();
    }

    private void assertLedgerMatches(LedgerIdPublicationTransactionBody body) {
        var ledger = blockProperties.getLedger();
        assertThat(ledger).isNotNull();
        assertThat(ledger.getLedgerId()).isEqualTo(toBytes(body.getLedgerId()));
        assertThat(ledger.getHistoryProofVerificationKey()).isEqualTo(toBytes(body.getHistoryProofVerificationKey()));
        assertThat(ledger.getNodeContributions()).hasSize(body.getNodeContributionsCount());

        var nc = body.getNodeContributions(0);
        assertThat(ledger.getNodeContributions().get(0))
                .returns(toBytes(nc.getHistoryProofKey()), c -> c.getHistoryProofKey())
                .returns(nc.getNodeId(), c -> c.getNodeId())
                .returns(nc.getWeight(), c -> c.getWeight());
    }
}
