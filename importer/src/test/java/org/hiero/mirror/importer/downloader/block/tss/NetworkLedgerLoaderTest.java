// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.tss.legacy.LedgerIdNodeContribution;
import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
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

    private static final String LEDGER_ID_FIXTURE_CLASSPATH = "networkledger/mainnet.bin";
    private static final Path LEDGER_ID_FIXTURE_PATH = Path.of("src/test/resources/" + LEDGER_ID_FIXTURE_CLASSPATH);

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
     * Reads the committed src/test/resources/networkledger/mainnet.bin fixture (a serialized
     * LedgerIdPublicationTransactionBody) and drives the loader through the classpath path. The fixture is generated by
     * {@link #generateLedgerIdFixture()} (disabled — run manually to regenerate).
     */
    @Test
    void loadFromLedgerIdFixtureClasspath() throws IOException {
        var bodyBytes = new ClassPathResource(LEDGER_ID_FIXTURE_CLASSPATH).getContentAsByteArray();

        importerProperties.setNetwork(HederaNetwork.MAINNET);
        when(resourceLoader.getResource("classpath:/networkledger/" + HederaNetwork.MAINNET))
                .thenReturn(new ByteArrayResource(bodyBytes));

        loader.load();

        assertFixtureLedgerLoaded();
    }

    /**
     * Reads the same fixture via the user-provided {@code initialLedgerIdPublication} path. Copies the classpath
     * fixture into the temp dir to keep the test independent of the working directory.
     */
    @Test
    void loadFromLedgerIdFixturePath() throws IOException {
        var bodyBytes = new ClassPathResource(LEDGER_ID_FIXTURE_CLASSPATH).getContentAsByteArray();
        var path = tempDir.resolve("mainnet.bin");
        Files.write(path, bodyBytes);
        blockProperties.setInitialLedgerIdPublication(path);

        loader.load();

        assertFixtureLedgerLoaded();
    }

    /**
     * Generates a synthetic LedgerIdPublicationTransactionBody and writes it to
     * src/test/resources/networkledger/mainnet.bin. Run manually to (re)generate the fixture.
     */
    @Test
    @Disabled("Run manually to regenerate src/test/resources/networkledger/mainnet.bin")
    void generateLedgerIdFixture() throws IOException {
        var ledgerBody = recordItemBuilder
                .ledgerIdPublication()
                .build()
                .getTransactionBody()
                .getLedgerIdPublication();

        Files.createDirectories(LEDGER_ID_FIXTURE_PATH.getParent());
        Files.write(LEDGER_ID_FIXTURE_PATH, ledgerBody.toByteArray());
    }

    private void assertFixtureLedgerLoaded() {
        var ledger = blockProperties.getLedger();
        assertThat(ledger).isNotNull();
        assertThat(ledger.getLedgerId()).isNotEmpty();
        assertThat(ledger.getHistoryProofVerificationKey()).isNotEmpty();
        assertThat(ledger.getNodeContributions()).isNotEmpty();
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
