// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.hapi.block.stream.protoc.Block;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprConfigUpdate;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprControlConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprControlMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageReplyStatus;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.common.domain.clpr.ClprThrottle;
import org.hiero.mirror.common.domain.entity.EntityId;
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
import org.hiero.mirror.importer.repository.ClprChannelPendingCommitmentRepository;
import org.hiero.mirror.importer.repository.ClprChannelRepository;
import org.hiero.mirror.importer.repository.ClprConnectorRepository;
import org.hiero.mirror.importer.repository.ClprEndpointManifestRepository;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.hiero.mirror.importer.repository.ClprMessageRepository;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real blocks captured from a local {@code hiero-consensus-node} alpha5 ({@code build-00423}) subprocess network
 * running the full CLPR HAPI suite, rather than synthetic fixtures built via {@code RecordItemBuilder}. See
 * {@code data/blockstreams-clpr/README.md} for the full transaction-by-transaction breakdown of each captured
 * block.
 */
@RequiredArgsConstructor
class ClprBlockFileE2ETest extends ImporterIntegrationTest {

    private static final String CLPR_BLOCK_FILE_1 = "data/blockstreams-clpr/0000/0000/0000/0000/001.blk.zstd";
    private static final String CLPR_BLOCK_FILE_2 = "data/blockstreams-clpr/0000/0000/0000/0000/002.blk.zstd";
    private static final String CLPR_BLOCK_FILE_3 = "data/blockstreams-clpr/0000/0000/0000/0000/003.blk.zstd";
    private static final String CLPR_BLOCK_FILE_4 = "data/blockstreams-clpr/0000/0000/0000/0000/004.blk.zstd";

    private final BlockFileBuilder blockFileBuilder;
    private final BlockFileTransformer blockFileTransformer;
    private final BlockStreamReader blockStreamReader;
    private final BlockTransactionBuilder blockTransactionBuilder;
    private final ClprChannelPendingCommitmentRepository clprChannelPendingCommitmentRepository;
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
        // given/when -- block 1: ClprRegisterChannel, the commit half of the channel's commit-reveal
        parse(CLPR_BLOCK_FILE_1);

        // then -- only a pending commitment exists; the channel itself isn't created until the reveal (block 2)
        final var hex = HexFormat.of();
        final var pendingCommitmentCreatedTimestamp = 1794435392797325104L;

        final var expectedPendingChannel = domainBuilder
                .clprChannelPendingCommitment()
                .customize(builder -> builder.createdTimestamp(pendingCommitmentCreatedTimestamp)
                        .timestampRange(Range.atLeast(pendingCommitmentCreatedTimestamp))
                        .status(ClprPendingCommitmentStatus.OPEN)
                        .ownershipCommitment(
                                hex.parseHex("29a90d6ef6b307905b7b19e7e6e9c60431c8debb6589897129a5d7012e859f6b")))
                .get();

        assertThat(clprChannelRepository.count()).isZero();
        assertThat(clprChannelPendingCommitmentRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedPendingChannel);

        // given/when -- block 2: channel + connector commit-reveal, plus the user-level sendMessage call (lands in
        // the same block as the connector commit-reveal this time around)
        parse(CLPR_BLOCK_FILE_2);

        // then
        final var expectedMessage1 = domainBuilder
                .clprMessage()
                .customize(builder -> builder.consensusTimestamp(1794435394777634104L)
                        .messageId(1L)
                        .replyToMessageId(null)
                        .messageType(ClprMessageType.DATA)
                        .replyStatus(null)
                        .channelId(hex.parseHex("9632b28a4d876b868186c2cfe2b376ef3a6840482530d66599c93cae4532f330"))
                        .connectorId(hex.parseHex("5c6562ad6a560de52597bbf3f82516ce23aa695f44170b065a20275456f8466b"))
                        .controlMessage(null)
                        .messageData(hex.parseHex("010203"))
                        .runningHash(hex.parseHex("28a119e3240c6765e780d5e8a223eda67060a3eaef68246bf43b44f64e2bf66e"))
                        .sender(hex.parseHex("000000000000000000000000000000000000045d"))
                        .targetApplication(hex.parseHex("0000000000000000000000000000000000000000")))
                .get();

        final var channelCreatedTimestamp = 1794435393328435104L;
        final var connectorRegisteredTimestamp = 1794435393817627104L;
        final var connectorCompletedTimestamp = 1794435394297896104L;
        final var messageTimestamp = 1794435394777634104L;

        // The channel registration commitment transitions from OPEN to COMPLETED when the channel is revealed.
        final var expectedCompletedCommitment = expectedPendingChannel.toBuilder()
                .timestampRange(Range.atLeast(channelCreatedTimestamp))
                .status(ClprPendingCommitmentStatus.COMPLETED)
                .build();

        final var expectedHistoryCommitment = expectedPendingChannel.toBuilder()
                .timestampRange(Range.closedOpen(pendingCommitmentCreatedTimestamp, channelCreatedTimestamp))
                .build();

        assertThat(clprChannelPendingCommitmentRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedCompletedCommitment);
        assertThat(findHistory(ClprChannelPendingCommitment.class))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedHistoryCommitment);

        // Build expected snapshots as ClprChannel, then derive subsequent versions using toBuilder().
        // findHistory() maps embedded peerThrottle values from the history table.
        final var expectedChannelHistory = domainBuilder
                .clprChannel()
                .customize(builder -> builder.ackedMessageId(0L)
                        .createdTimestamp(channelCreatedTimestamp)
                        .timestampRange(Range.closedOpen(channelCreatedTimestamp, messageTimestamp))
                        .status(ClprChannelStatus.ACTIVE)
                        .signatureScheme(ClprSignatureScheme.ED25519)
                        .channelId(expectedMessage1.getChannelId())
                        .chainId("hiero:testing")
                        .channelContext(
                                hex.parseHex(
                                        "9632b28a4d876b868186c2cfe2b376ef3a6840482530d66599c93cae4532f330000000000000000000000000000000000000016e"))
                        .configProofBytes(
                                hex.parseHex(
                                        "0a3f223d1a3bda0338120d686965726f3a74657374696e671a14000000000000000000000000000000000000016e2a1108641080800418c0843d20e80728808040"))
                        .endpointManifest(List.of())
                        .endpointManifestProofBytes(new byte[0])
                        .endpointManifestVersion(0L)
                        .lastConfigTimestamp(channelCreatedTimestamp)
                        .nextMessageId(1L)
                        .ownershipCommitment(expectedPendingChannel.getOwnershipCommitment())
                        .peerConfigTimestamp(0L)
                        .peerThrottle(ClprThrottle.builder()
                                .maxGasPerMessage(1_000_000L)
                                .maxLocalEndpoints(0L)
                                .maxMessagePayloadBytes(65_536L)
                                .maxMessagesPerBundle(100L)
                                .maxPeerEndpoints(0L)
                                .maxQueueDepth(1_000L)
                                .maxSyncBytes(1_048_576L)
                                .build())
                        .publicKey(hex.parseHex("aeec43fb5f289cf17a70014dfa46a3e5a7035a784a5d6896abeba1f3464e7670"))
                        .receivedMessageId(0L)
                        .receivedRunningHash(new byte[32])
                        .sentRunningHash(new byte[32])
                        .signature(
                                hex.parseHex(
                                        "d5de09f2d288702e268a987aff63e919af3f01d3663ce391bef0edc69e54f9b6d4d7f33146b828843681acd036d237b5a0f42f52f41b4b71704cebcd3e0bca00"))
                        .trustAnchor(new byte[0])
                        .trustAnchorId(new byte[0])
                        .verifierContractId(EntityId.of(198161682139186265L))
                        .verifierFingerprint(
                                hex.parseHex("b2edf337f9fbaa46115dcef992793e3e9c97a44f4118c25ae3805bd97a06e0a0")))
                .get();

        // sendMessage advances the outbound running hash to the hash persisted with message 1.
        final var expectedChannel = expectedChannelHistory.toBuilder()
                .timestampRange(Range.atLeast(messageTimestamp))
                .nextMessageId(2L)
                .sentRunningHash(expectedMessage1.getRunningHash())
                .build();

        assertThat(findHistory(ClprChannel.class))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedChannelHistory);
        assertThat(clprChannelRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedChannel);

        // Connector history is also mapped into ClprConnector; each snapshot builds on the previous one.
        // The pending connector is keyed by (channelId, commitment) until ClprCompleteConnector reveals its ID.
        final var expectedPendingConnectorHistory = domainBuilder
                .clprConnector()
                .customize(builder -> builder.channelId(expectedMessage1.getChannelId())
                        .commitment(hex.parseHex("cd30c2cc969c62e2cd886e2ee7c1d33a72f5515530c431808693f68861db77f6"))
                        .createdTimestamp(connectorRegisteredTimestamp)
                        .timestampRange(Range.closedOpen(connectorRegisteredTimestamp, connectorCompletedTimestamp))
                        .status(ClprConnectorStatus.PENDING)
                        .inFlightMessageCount(0L)
                        .lockedStake(0L)
                        .slashCount(0L)
                        .adminKey(null)
                        .connectorContractId(null)
                        .connectorId(null)
                        .publicKey(null)
                        .salt(null)
                        .signature(null)
                        .signatureScheme(null)
                        .stakeRecipientId(null))
                .get();

        // Completion reveals the connector fields and resets createdTimestamp to completion time.
        final var expectedActiveConnectorHistory = expectedPendingConnectorHistory.toBuilder()
                .createdTimestamp(connectorCompletedTimestamp)
                .timestampRange(Range.closedOpen(connectorCompletedTimestamp, messageTimestamp))
                .status(ClprConnectorStatus.ACTIVE)
                .connectorContractId(EntityId.of(198161682139186267L))
                .connectorId(expectedMessage1.getConnectorId())
                .adminKey(hex.parseHex("32240a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92"))
                .publicKey(expectedChannelHistory.getPublicKey())
                .salt(hex.parseHex("efe0dd6938db954593871ae6eed156c607019a587aa0dfb6a9f58815a2a9cc20"))
                .signature(
                        hex.parseHex(
                                "1f8ea5a6ff78100ca0d5bf5939a7a251c68459c7fd95c0c4a5bd40ffd0a3a4e31ccfc34ae0d7a99e5c8573f52d5262fe74be7581714c71fbfdf7082cb53e5806"))
                .signatureScheme(ClprSignatureScheme.ED25519)
                .lockedStake(100_000_000L)
                .build();

        // sendMessage increments the connector's in-flight count without changing its other revealed fields.
        final var expectedConnector = expectedActiveConnectorHistory.toBuilder()
                .timestampRange(Range.atLeast(messageTimestamp))
                .inFlightMessageCount(1L)
                .build();

        assertThat(findHistory(ClprConnector.class))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(expectedPendingConnectorHistory, expectedActiveConnectorHistory);
        assertThat(clprConnectorRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedConnector);

        assertThat(clprLedgerConfigurationRepository.count()).isZero();
        assertThat(clprMessageRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedMessage1);

        // given/when -- block 3: submitBundle replying to block 2's sendMessage
        parse(CLPR_BLOCK_FILE_3);

        // then -- the missing destination connector produces a RESPONSE to message 1.
        final var bundleTimestamp = 1794435395317570104L;
        final var expectedMessage2 = expectedMessage1.toBuilder()
                .consensusTimestamp(bundleTimestamp)
                .messageId(2L)
                .replyToMessageId(1L)
                .messageType(ClprMessageType.RESPONSE)
                .replyStatus(ClprMessageReplyStatus.CONNECTOR_NOT_FOUND)
                .connectorId(null)
                .messageData(new byte[0])
                .runningHash(hex.parseHex("7260766a7dfa342beeb9f55c5c3db4caa2379d3cea8cc26ca8904667bad171c5"))
                .sender(null)
                .targetApplication(null)
                .build();

        assertThat(clprMessageRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(expectedMessage1, expectedMessage2);

        // The channel's state from after sendMessage becomes its second history version when the bundle
        // advances it to CLOSING. The first history row remains unchanged.
        final var expectedChannelAfterMessageHistory = expectedChannel.toBuilder()
                .timestampRange(Range.closedOpen(messageTimestamp, bundleTimestamp))
                .build();

        final var expectedChannelAfterBundle = expectedChannelAfterMessageHistory.toBuilder()
                .timestampRange(Range.atLeast(bundleTimestamp))
                .status(ClprChannelStatus.CLOSING)
                .nextMessageId(3L)
                .receivedMessageId(1L)
                .receivedRunningHash(hex.parseHex("6c61869fd21bc5e7d9cb513dc566c01f00a820bcc3a80cdd103aaaed98194eae"))
                .sentRunningHash(expectedMessage2.getRunningHash())
                .build();

        assertThat(clprChannelRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedChannelAfterBundle);
        assertThat(findHistory(ClprChannel.class))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(expectedChannelHistory, expectedChannelAfterMessageHistory);

        // The bundle doesn't modify the connector or its two existing history rows.
        assertThat(clprConnectorRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedConnector);
        assertThat(findHistory(ClprConnector.class))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(expectedPendingConnectorHistory, expectedActiveConnectorHistory);

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
        final var expectedManifest =
                recordItem.getClprContext().getClprEndpointManifestUpdate().getClprEndpointManifestValue();
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
        // Block 4 comes from the independent multi-network capture.
        parse(CLPR_BLOCK_FILE_4);

        final var hex = HexFormat.of();
        final var configurationTimestamp = 1794435874358368104L;
        final var messageTimestamp = 1794435875897776104L;
        final var channelId = hex.parseHex("0a5159d65d68a5b04646d46b8b3a6ad73d9ad5fab3389bcb4acc597d441abd69");
        final var connectorId = hex.parseHex("a98476d05a43cb255e83fb33ceac99a26002830ae618bf49ff4ff50d3ea5674c");
        final var initialTrustAnchor = hex.parseHex(
                "d41a1c8223369d0ae087e23ba398efd76c2808d67666487673ee298093d3811b2d6e0c92fb2e29bf7b6bc7b4e487af70f4edf477aa82a426c03e53f90ded542d");
        final var serviceAddress = hex.parseHex("000000000000000000000000000000000000016e");

        // ClprLedgerConfiguration from the first retained transaction.
        final var expectedThrottle = ClprThrottle.builder()
                .maxGasPerMessage(1_000_000L)
                .maxLocalEndpoints(0L)
                .maxMessagePayloadBytes(65_536L)
                .maxMessagesPerBundle(6L)
                .maxPeerEndpoints(0L)
                .maxQueueDepth(1_000L)
                .maxSyncBytes(1_048_576L)
                .build();

        final var expectedLedgerConfiguration = domainBuilder
                .clprLedgerConfiguration()
                .customize(builder -> builder.consensusTimestamp(configurationTimestamp)
                        .chainId("eip155:298")
                        .initialTrustAnchor(initialTrustAnchor)
                        .initialTrustAnchorId(initialTrustAnchor)
                        .protocolVersion(1L)
                        .serviceAddress(serviceAddress)
                        .throttle(expectedThrottle))
                .get();

        assertThat(clprLedgerConfigurationRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(expectedLedgerConfiguration);

        // A CONTROL message propagates the same configuration to the peer ledger.
        final var expectedControlConfiguration = ClprControlConfiguration.builder()
                .chainId(expectedLedgerConfiguration.getChainId())
                .initialTrustAnchor(expectedLedgerConfiguration.getInitialTrustAnchor())
                .initialTrustAnchorId(expectedLedgerConfiguration.getInitialTrustAnchorId())
                .protocolVersion(expectedLedgerConfiguration.getProtocolVersion())
                .serviceAddress(expectedLedgerConfiguration.getServiceAddress())
                .throttle(expectedLedgerConfiguration.getThrottle())
                .build();

        final var expectedControlMessage = domainBuilder
                .clprControlMessage()
                .customize(builder -> builder.consensusTimestamp(messageTimestamp)
                        .messageId(7L)
                        .channelId(channelId)
                        .connectorId(null)
                        .messageType(ClprMessageType.CONTROL)
                        .replyStatus(null)
                        .replyToMessageId(null)
                        .controlMessage(ClprControlMessage.builder()
                                .configUpdate(ClprConfigUpdate.builder()
                                        .configuration(expectedControlConfiguration)
                                        .build())
                                .build())
                        .messageData(null)
                        .runningHash(hex.parseHex("805f8dd48d471a11f3b738331c00ffa43ec7bfce9942eea24aef140649c602c1"))
                        .sender(null)
                        .targetApplication(null))
                .get();

        // Six DATA messages sent with payloads "msg-" + 31 zero bytes + the sequence byte (0x06..0x0b).
        final var messageDataPrefix = "6d73672d" + "00".repeat(31);
        final var expectedDataMessage1 = domainBuilder
                .clprMessage()
                .customize(builder -> builder.consensusTimestamp(messageTimestamp)
                        .messageId(8L)
                        .channelId(channelId)
                        .connectorId(connectorId)
                        .messageType(ClprMessageType.DATA)
                        .replyStatus(null)
                        .replyToMessageId(null)
                        .controlMessage(null)
                        .messageData(hex.parseHex(messageDataPrefix + "06"))
                        .runningHash(hex.parseHex("a810bf2849fd9414f83735c20436584950ecf46c2c44984b06f6e008e8c6822d"))
                        .sender(hex.parseHex("0000000000000000000000000000000000000436"))
                        .targetApplication(hex.parseHex("0000000000000000000000000000000000000000")))
                .get();

        final var expectedDataMessage2 = expectedDataMessage1.toBuilder()
                .messageId(9L)
                .messageData(hex.parseHex(messageDataPrefix + "07"))
                .runningHash(hex.parseHex("2c8cd92d745ab0790ee2e9548d43d93216a9321d76f9c41b4b8276b240edc5af"))
                .build();

        final var expectedDataMessage3 = expectedDataMessage1.toBuilder()
                .messageId(10L)
                .messageData(hex.parseHex(messageDataPrefix + "08"))
                .runningHash(hex.parseHex("47cf102358ebd997ef9dfcd52e2b897976a34327ca17d5fb1f56bf72fd6166e8"))
                .build();

        final var expectedDataMessage4 = expectedDataMessage1.toBuilder()
                .messageId(11L)
                .messageData(hex.parseHex(messageDataPrefix + "09"))
                .runningHash(hex.parseHex("0399e044d2655817e4f5a40746fb76d166f30d233d857290a454535a0ed5ed6d"))
                .build();

        final var expectedDataMessage5 = expectedDataMessage1.toBuilder()
                .messageId(12L)
                .messageData(hex.parseHex(messageDataPrefix + "0a"))
                .runningHash(hex.parseHex("206638bf900bd62530fcae2bc78915ba0bfdfe3dc25cf30c81912528a32035c3"))
                .build();

        final var expectedDataMessage6 = expectedDataMessage1.toBuilder()
                .messageId(13L)
                .messageData(hex.parseHex(messageDataPrefix + "0b"))
                .runningHash(hex.parseHex("27384f8cb9fa4e207a90be8d4e460bdbd08c44f1b5bd9f9ef603c5a3e15dc4b0"))
                .build();

        assertThat(clprMessageRepository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(
                        expectedControlMessage,
                        expectedDataMessage1,
                        expectedDataMessage2,
                        expectedDataMessage3,
                        expectedDataMessage4,
                        expectedDataMessage5,
                        expectedDataMessage6);
    }

    //    @Test
    //    void clprCloseChannelAdminSweep() {
    //        // TODO: no real or synthetic block built yet for an admin ClprCloseChannel sweeping an abandoned,
    //        // never-completed channel registration; replace with real assertions once one exists.
    //        fail("No block exercises CLPR admin sweep of an abandoned channel commitment end-to-end yet");
    //    }
    //
    //    @Test
    //    void clprSendMessageInsideHookDispatch() {
    //        // TODO: no real block captured yet for a CryptoTransfer/TokenAirdrop/TokenClaimAirdrop hook calling
    //        // sendMessage; replace with real assertions once one exists.
    //        fail("No real block captured yet for CLPR sendMessage inside an EVM hook dispatch, see "
    //                + "CLPR_CHILD_STATE_CHANGE_GAP.md");
    //    }
    //
    //    @Test
    //    void clprSendMessageInsideAtomicBatch() {
    //        // TODO: no real block captured yet for an AtomicBatch inner transaction calling sendMessage; replace with
    //        // real assertions once one exists.
    //        fail("No real block captured yet for CLPR sendMessage inside an AtomicBatch inner transaction, see "
    //                + "CLPR_CHILD_STATE_CHANGE_GAP.md");
    //    }

    private void parse(String resource) throws Exception {
        final var file = TestUtils.getResource(resource);
        final var streamFileData = StreamFileData.from(file);
        final var bytes = streamFileData.getBytes();
        final var block = Block.parseFrom(streamFileData.getInputStream());
        final var loadStart = System.currentTimeMillis();
        final var blockStream = new BlockStream(
                BlockStreamTestUtils.toUnparsed(block.getItemsList()), loadStart, bytes, file.getName(), bytes.length);
        final var blockFile = blockStreamReader.read(blockStream);
        final var recordFile = blockFileTransformer.transform(blockFile);
        recordFileParser.parse(recordFile);
    }
}
