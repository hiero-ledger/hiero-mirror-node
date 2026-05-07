// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.tss.legacy.LedgerIdNodeContribution;
import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NetworkLedgerLoaderTest {

    @TempDir
    private Path tempDir;

    private BlockProperties blockProperties;
    private NetworkLedgerLoader loader;

    @BeforeEach
    void setup() {
        blockProperties = new BlockProperties(new ImporterProperties());
        loader = new NetworkLedgerLoader(blockProperties, new LedgerIdPublicationTransactionParser());
    }

    @Test
    void loadWhenInitialLedgerIdPublicationNotSet() {
        loader.load();

        assertThat(blockProperties.getLedger()).isNull();
    }

    @Test
    void loadFromInitialLedgerIdPublication() throws IOException {
        var body = buildProtoBody();
        var path = tempDir.resolve("ledger");
        Files.write(path, body.toByteArray());
        blockProperties.setInitialLedgerIdPublication(path);

        loader.load();

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

    @Test
    void loadWithMalformedBytes() throws IOException {
        var path = tempDir.resolve("ledger");
        Files.write(path, new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        blockProperties.setInitialLedgerIdPublication(path);

        assertThatThrownBy(loader::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse initialLedgerIdPublication file");
    }

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
}
