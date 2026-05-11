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
import java.util.List;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.junit.jupiter.api.BeforeEach;
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
