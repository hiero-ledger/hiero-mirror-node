// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.hapi.block.stream.protoc.Block;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.clpr.ClprChannelHistory;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprConnectorHistory;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.downloader.block.BlockFileTransformer;
import org.hiero.mirror.importer.parser.domain.BlockFileBuilder;
import org.hiero.mirror.importer.parser.domain.BlockTransactionBuilder;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.hiero.mirror.importer.reader.block.BlockStreamTestUtils;
import org.hiero.mirror.importer.repository.ClprChannelRepository;
import org.hiero.mirror.importer.repository.ClprConnectorRepository;
import org.hiero.mirror.importer.repository.ClprEndpointManifestRepository;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.hiero.mirror.importer.repository.ClprMessageRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real blocks captured from a local {@code hiero-consensus-node} v0.80.0-alpha.3 subprocess network running
 * {@code ClprSubmitBundleSuite.closedPeerTriggersClosingOnActiveChannel}, rather than synthetic fixtures built via
 * {@code RecordItemBuilder}. See {@code data/blockstreams-clpr/README.md} for the full transaction-by-transaction
 * breakdown of each captured block.
 *
 * <p>Block 1 (block 13 of the source run) covers the channel/connector commit-reveal lifecycle (register + complete
 * for both), plus a {@code ContractCall} dispatched to the verifier contract as part of completing the channel
 * ({@code ClprPassThroughVerifier.verifyConfig}) — not a user-level {@code sendMessage} call.
 *
 * <p>Block 2 (block 14, the very next real block from the same run) covers a user-level {@code sendMessage}
 * {@code ContractCall} to the CLPR system contract, a {@code ClprSubmitBundle} carrying an inbound message (which
 * transitions the channel to {@code CLOSING} per the peer-closed bundle in this test scenario), the resulting
 * internal {@code ClprPassThroughVerifier.verifyBundle} dispatch, and a {@code ClprCloseChannel} attempt that fails
 * with {@code CLPR_INVALID_CHANNEL_STATUS} (expected — the channel isn't {@code ACTIVE} anymore).
 *
 * <p>Known scope limit: this is a single-network capture, so it doesn't cover cross-network message ack/delivery,
 * which only the multi-network {@code ClprMessagesSuite} round-trip exercises. It also doesn't exercise
 * {@code clpr_ledger_configuration} — the suite's {@code clprUpdateLedgerConfiguration} call landed in a later block
 * of the same run, not captured here.
 *
 * <p>{@link #clprMaintenance()} is the one exception to "real captured blocks only": {@code ClprMaintenance} only
 * fires when the consensus node's per-round endpoint-manifest reconciler actually changes something (genesis,
 * post-upgrade, or a real roster/endpoint change), none of which this capture run triggers, so it uses a synthetic
 * block built via {@code RecordItemBuilder}/{@code BlockTransactionBuilder} instead.
 *
 * <p>Block 3 ({@link #clprControlMessageConfigPropagation()}) is a separate capture from a different, two-network
 * run ({@code ClprHieroToHieroSuite.configUpdateMidStreamBumpsBundleCap}) and is parsed standalone rather than
 * chained after blocks 1/2: its transactions don't carry a {@code ClprChannel} map-update (the channel's fields are
 * unchanged by this bundle), so no {@code clpr_channel} row exists unless blocks 1/2 are parsed first.
 * {@code clpr_message} has no such dependency -- it's insert-only with no foreign key -- so this fixture only
 * asserts on message rows. Mirror-node processing doesn't distinguish single- vs. multi-network captures; both are
 * just blocks to parse.
 */
@RequiredArgsConstructor
class ClprBlockFileE2ETest extends ImporterIntegrationTest {

    private static final String CLPR_BLOCK_FILE_1 = "data/blockstreams-clpr/0000/0000/0000/0000/001.blk.zstd";
    private static final String CLPR_BLOCK_FILE_2 = "data/blockstreams-clpr/0000/0000/0000/0000/002.blk.zstd";
    private static final String CLPR_BLOCK_FILE_3 = "data/blockstreams-clpr/0000/0000/0000/0000/003.blk.zstd";

    private final BlockFileBuilder blockFileBuilder;
    private final BlockFileTransformer blockFileTransformer;
    private final BlockStreamReader blockStreamReader;
    private final BlockTransactionBuilder blockTransactionBuilder;
    private final ClprChannelRepository clprChannelRepository;
    private final ClprConnectorRepository clprConnectorRepository;
    private final ClprEndpointManifestRepository clprEndpointManifestRepository;
    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;
    private final ClprMessageRepository clprMessageRepository;
    private final RecordFileParser recordFileParser;
    private final RecordItemBuilder recordItemBuilder;
    private final TransactionRepository transactionRepository;

    @BeforeEach
    void setup() {
        // RecordFileParser's hash-chain check is in-memory and shared across test classes in this Spring context;
        // reset it so another CLPR fixture class running first doesn't break this one's chain check.
        recordFileParser.clear();
    }

    @Test
    void clprBlockFileLifecycle() throws Exception {
        // given/when -- block 1: channel + connector commit-reveal
        parse(CLPR_BLOCK_FILE_1);

        // then
        assertThat(clprChannelRepository.findAll()).singleElement().satisfies(channel -> {
            assertThat(channel.getStatus()).isEqualTo(ClprChannelStatus.ACTIVE);
            assertThat(channel.getChainId()).isEqualTo("hiero:testing");
            assertThat(channel.getNextMessageId()).isEqualTo(1L);
            assertThat(channel.getVerifierContractId()).isNotNull();
        });
        assertThat(clprConnectorRepository.findAll()).singleElement().satisfies(connector -> {
            assertThat(connector.getLockedStake()).isEqualTo(100_000_000L);
            assertThat(connector.getSlashCount()).isZero();
            assertThat(connector.getInFlightMessageCount()).isZero();
            assertThat(connector.getConnectorContractId()).isNotNull();
            assertThat(connector.getStatus()).isNotEqualTo(ClprConnectorStatus.DELETED);
        });
        assertThat(clprLedgerConfigurationRepository.count()).isZero();
        assertThat(clprMessageRepository.count()).isZero();

        // given/when -- block 2: sendMessage + submitBundle on the same channel/connector
        parse(CLPR_BLOCK_FILE_2);

        // then
        assertThat(clprChannelRepository.findAll()).singleElement().satisfies(channel -> {
            assertThat(channel.getStatus()).isEqualTo(ClprChannelStatus.CLOSING);
            assertThat(channel.getReceivedMessageId()).isEqualTo(1L);
        });
        assertThat(clprConnectorRepository.count()).isOne();
        // messageId 1 (the outbound DATA sendMessage) and messageId 2 (its auto-generated CONNECTOR_NOT_FOUND
        // RESPONSE, since this single-network capture has no real peer connector to deliver to)
        assertThat(clprMessageRepository.count()).isEqualTo(2L);

        // every channel write historizes for now (placeholder until the stream exposes real per-field diffs); the
        // connector wasn't touched by this bundle, so no connector history row
        assertThat(findHistory(ClprChannelHistory.class)).hasSize(2).allSatisfy(history -> {
            assertThat(history.getStatus()).isEqualTo(ClprChannelStatus.ACTIVE);
            assertThat(history.getTimestampRange().hasUpperBound()).isTrue();
        });
        assertThat(findHistory(ClprConnectorHistory.class)).isEmpty();

        assertThat(transactionRepository.count()).isGreaterThanOrEqualTo(9L);
    }

    @Test
    // TODO: generate real block file
    void clprMaintenance() {
        // given -- synthetic block, see class javadoc for why
        final var recordItem = recordItemBuilder
                .clprMaintenance()
                .recordItem(r -> r.congestionPricingMultiplier(0L))
                .build();
        final var blockTransaction =
                blockTransactionBuilder.clprMaintenance(recordItem).build();
        final var blockFile = blockFileBuilder.items(List.of(blockTransaction)).build();

        // when
        final var recordFile = blockFileTransformer.transform(blockFile);
        recordFileParser.parse(recordFile);

        // then
        final var expectedManifest = recordItem.getClprContext().getClprEndpointManifest();
        assertThat(clprEndpointManifestRepository.findAll()).singleElement().satisfies(manifest -> {
            assertThat(manifest.getConsensusTimestamp()).isEqualTo(recordItem.getConsensusTimestamp());
            assertThat(manifest.getVersion()).isEqualTo(expectedManifest.getVersion());
            assertThat(manifest.getServiceAddress())
                    .isEqualTo(DomainUtils.toBytes(expectedManifest.getServiceAddress()));
            assertThat(manifest.getEndpoints()).hasSize(1);
        });
    }

    @Test
    void clprControlMessageConfigPropagation() throws Exception {
        // given/when -- parsed standalone, see class javadoc
        parse(CLPR_BLOCK_FILE_3);

        // then
        assertThat(clprMessageRepository.count()).isEqualTo(7L);
        assertThat(clprMessageRepository.findAll())
                .filteredOn(m -> m.getMessageType() == ClprMessageType.CONTROL)
                .singleElement()
                .satisfies(control -> {
                    assertThat(control.getMessageId()).isEqualTo(7L);
                    assertThat(control.getControlMessage()).isNotNull();
                    final var configUpdate = control.getControlMessage().getConfigUpdate();
                    assertThat(configUpdate).isNotNull();
                    assertThat(configUpdate.getConfiguration().getThrottle().getMaxMessagesPerBundle())
                            .isEqualTo(6L);
                });
        assertThat(clprMessageRepository.findAll())
                .filteredOn(m -> m.getMessageType() == ClprMessageType.DATA)
                .hasSize(6);
    }

    @Test
    void clprSendMessageInsideHookDispatch() {
        // TODO: no real block captured yet for a CryptoTransfer/TokenAirdrop/TokenClaimAirdrop hook calling
        // sendMessage; replace with real assertions once one exists.
        fail("No real block captured yet for CLPR sendMessage inside an EVM hook dispatch, see "
                + "CLPR_CHILD_STATE_CHANGE_GAP.md");
    }

    @Test
    void clprSendMessageInsideAtomicBatch() {
        // TODO: no real block captured yet for an AtomicBatch inner transaction calling sendMessage; replace with
        // real assertions once one exists.
        fail("No real block captured yet for CLPR sendMessage inside an AtomicBatch inner transaction, see "
                + "CLPR_CHILD_STATE_CHANGE_GAP.md");
    }

    private void parse(String resource) throws Exception {
        final var file = TestUtils.getResource(resource);
        final var streamFileData = StreamFileData.from(file);
        final var bytes = streamFileData.getBytes();
        final var block = Block.parseFrom(streamFileData.getInputStream());
        final var loadStart = System.currentTimeMillis();
        final var blockStream = new BlockStream(
                BlockStreamTestUtils.toUnparsed(block.getItemsList()),
                loadStart,
                bytes,
                file.getName(),
                loadStart - 1000,
                bytes.length);
        final var blockFile = blockStreamReader.read(blockStream);
        final var recordFile = blockFileTransformer.transform(blockFile);
        recordFileParser.parse(recordFile);
    }
}
