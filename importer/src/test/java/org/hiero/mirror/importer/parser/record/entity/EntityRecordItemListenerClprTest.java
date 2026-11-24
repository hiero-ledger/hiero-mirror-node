// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason.ENDPOINT_MANIFEST_RECONCILIATION_VALUE;
import static com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason.GENESIS_INITIALIZATION_VALUE;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprChannelMapUpdate;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprConnectorMapUpdate;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hederahashgraph.api.proto.java.ClprChannelStatus;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import com.hederahashgraph.api.proto.java.ClprMessageKey;
import com.hederahashgraph.api.proto.java.ClprMessagePayload;
import com.hederahashgraph.api.proto.java.ClprMessageValue;
import com.hederahashgraph.api.proto.java.ClprThrottles;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprMaintenance;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.StateChangeContext;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.ClprChannelRepository;
import org.hiero.mirror.importer.repository.ClprConnectorRepository;
import org.hiero.mirror.importer.repository.ClprEndpointManifestRepository;
import org.hiero.mirror.importer.repository.ClprLedgerConfigurationRepository;
import org.hiero.mirror.importer.repository.ClprMaintenanceRepository;
import org.hiero.mirror.importer.repository.ClprMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class EntityRecordItemListenerClprTest extends AbstractEntityRecordItemListenerTest {

    private final ClprChannelRepository clprChannelRepository;
    private final ClprConnectorRepository clprConnectorRepository;
    private final ClprEndpointManifestRepository clprEndpointManifestRepository;
    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;
    private final ClprMaintenanceRepository clprMaintenanceRepository;
    private final ClprMessageRepository clprMessageRepository;

    @BeforeEach
    @AfterEach
    void resetPersistProperties() {
        entityProperties.getPersist().setClpr(true);
    }

    @Test
    void clprCreateChannel() {
        final var channelId = recordItemBuilder.bytes(32);
        final var channelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChainId("hedera:mainnet")
                .setChannelId(channelId)
                .setStatus(ClprChannelStatus.ACTIVE)
                .setVerifierContract(recordItemBuilder.contractId())
                .setVerifierFingerprint(recordItemBuilder.bytes(32))
                .build();
        final var recordItem = recordItemBuilder
                .clprCreateChannel()
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new StateChangeContext.ClprChannelEntry(
                                channelId, clprChannelMapUpdate(channelId, channelValue)))))
                .build();
        final var body = recordItem.getTransactionBody().getClprCreateChannel();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.findById(DomainUtils.toBytes(channelId)))
                .get()
                .returns(recordItem.getConsensusTimestamp(), ClprChannel::getCreatedTimestamp)
                .returns(channelValue.getChainId(), ClprChannel::getChainId)
                .returns(EntityId.of(channelValue.getVerifierContract()), ClprChannel::getVerifierContractId)
                .returns(
                        DomainUtils.toBytes(channelValue.getVerifierFingerprint()), ClprChannel::getVerifierFingerprint)
                .returns(DomainUtils.toBytes(body.getPeerVerifier()), ClprChannel::getPeerVerifier)
                .returns(DomainUtils.toBytes(body.getSalt()), ClprChannel::getSalt)
                .returns(DomainUtils.toBytes(body.getConfigProofBytes()), ClprChannel::getConfigProofBytes)
                .returns(
                        DomainUtils.toBytes(body.getEndpointManifestProofBytes()),
                        ClprChannel::getEndpointManifestProofBytes)
                .returns(channelValue.getStatusValue(), ClprChannel::getStatus)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprChannel::getTimestampRange);
    }

    @Test
    void clprCreateChannelInvalidTransaction() {
        final var recordItem = recordItemBuilder
                .clprCreateChannel()
                .status(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.count()).isZero();
    }

    @Test
    void clprDeleteChannel() {
        // given -- a live channel row must already exist since ClprDeleteChannel only ever patches counters/status
        final var completeRecordItem = recordItemBuilder.clprCreateChannel().build();
        parseRecordItemAndCommit(completeRecordItem);
        final var channelId =
                completeRecordItem.getTransactionBody().getClprCreateChannel().getChannelId();
        final var channelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setAckedMessageId(4)
                .setChannelId(channelId)
                .setNextMessageId(5)
                .setReceivedMessageId(3)
                .setReceivedRunningHash(recordItemBuilder.bytes(32))
                .setSentRunningHash(recordItemBuilder.bytes(32))
                .setStatus(ClprChannelStatus.CLOSING)
                .build();
        final var recordItem = recordItemBuilder
                .clprDeleteChannel()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new StateChangeContext.ClprChannelEntry(
                                channelId, clprChannelMapUpdate(channelId, channelValue)))))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.findById(DomainUtils.toBytes(channelId)))
                .get()
                .returns(4L, ClprChannel::getAckedMessageId)
                .returns(5L, ClprChannel::getNextMessageId)
                .returns(3L, ClprChannel::getReceivedMessageId)
                .returns(ClprChannelStatus.CLOSING_VALUE, ClprChannel::getStatus)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprChannel::getTimestampRange);
    }

    @Test
    void clprDeleteChannelSweepsAbandonedCommitment() { // TODO:// Should be able to delete this
        // given -- no ClprChannel state change is present, e.g. an admin sweep of a legacy pending ownership
        // commitment from a network that predates ClprCreateChannel; no such commitment can exist in this mirror
        // node's data, so nothing is written
        final var recordItem = recordItemBuilder
                .clprDeleteChannel()
                .transactionBody(b -> b.setOwnershipCommitment(DomainUtils.fromBytes(domainBuilder.bytes(32))))
                .recordItem(r -> r.clprContextBuilder().clprChannels(List.of()))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.count()).isZero();
    }

    @Test
    void clprDeleteChannelInvalidTransaction() {
        final var recordItem = recordItemBuilder
                .clprDeleteChannel()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.count()).isZero();
    }

    @Test
    void clprCreateConnector() {
        final var recordItem = recordItemBuilder.clprCreateConnector().build();
        final var body = recordItem.getTransactionBody().getClprCreateConnector();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.findAll())
                .hasSize(1)
                .first()
                .returns(DomainUtils.toBytes(body.getAdminKey().toByteString()), ClprConnector::getAdminKey)
                .returns(DomainUtils.toBytes(body.getChannelId()), ClprConnector::getChannelId)
                .returns(EntityId.of(body.getConnectorContract()), ClprConnector::getConnectorContractId)
                .returns(DomainUtils.toBytes(body.getConnectorId()), ClprConnector::getConnectorId)
                .returns(recordItem.getConsensusTimestamp(), ClprConnector::getCreatedTimestamp)
                .returns(body.getLockedStake(), ClprConnector::getLockedStake)
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprConnector::getTimestampRange);
    }

    @Test
    void clprCreateConnectorInvalidTransaction() {
        final var recordItem = recordItemBuilder
                .clprCreateConnector()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.count()).isZero();
    }

    @Test
    void clprDeleteConnector() {
        // given -- a registered/completed connector row must already exist since deregistration looks up its commitment
        final var seedConnector = domainBuilder.clprConnector().persist();
        final var recordItem = recordItemBuilder
                .clprDeleteConnector()
                .transactionBody(b -> b.setChannelId(DomainUtils.fromBytes(seedConnector.getChannelId()))
                        .setConnectorId(DomainUtils.fromBytes(seedConnector.getConnectorId())))
                .build();
        final var body = recordItem.getTransactionBody().getClprDeleteConnector();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.findById(
                        new AbstractClprConnector.Id(seedConnector.getChannelId(), seedConnector.getConnectorId())))
                .get()
                .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus)
                .returns(EntityId.of(body.getStakeRecipient()), ClprConnector::getStakeRecipientId)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprConnector::getTimestampRange);
    }

    @Test
    void clprDeleteConnectorInvalidTransaction() {
        final var seedConnector = domainBuilder.clprConnector().persist();
        final var recordItem = recordItemBuilder
                .clprDeleteConnector()
                .transactionBody(b -> b.setChannelId(DomainUtils.fromBytes(seedConnector.getChannelId()))
                        .setConnectorId(DomainUtils.fromBytes(seedConnector.getConnectorId())))
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.findById(
                        new AbstractClprConnector.Id(seedConnector.getChannelId(), seedConnector.getConnectorId())))
                .get()
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus);
    }

    @Test
    void clprSubmitBundle() {
        // given -- the live channel row and both referenced connector rows must already exist
        final var completeRecordItem = recordItemBuilder.clprCreateChannel().build();
        parseRecordItemAndCommit(completeRecordItem);
        final var channelId =
                completeRecordItem.getTransactionBody().getClprCreateChannel().getChannelId();

        final var patchedConnectorId = domainBuilder.bytes(32);
        domainBuilder
                .clprConnector()
                .customize(c -> c.channelId(DomainUtils.toBytes(channelId)).connectorId(patchedConnectorId))
                .persist();
        final var removedConnectorId = domainBuilder.bytes(32);
        domainBuilder
                .clprConnector()
                .customize(c -> c.channelId(DomainUtils.toBytes(channelId)).connectorId(removedConnectorId))
                .persist();

        final var channelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setAckedMessageId(0)
                .setChannelId(channelId)
                .setNextMessageId(2)
                .setReceivedMessageId(0)
                .setReceivedRunningHash(recordItemBuilder.bytes(32))
                .setSentRunningHash(recordItemBuilder.bytes(32))
                .setStatus(ClprChannelStatus.ACTIVE)
                .build();
        final var messageKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(1)
                .build();
        final var messageValue = ClprMessageValue.newBuilder()
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessage(com.hederahashgraph.api.proto.java.ClprMessage.newBuilder()
                                .setConnectorId(DomainUtils.fromBytes(patchedConnectorId))
                                .setMessageData(recordItemBuilder.bytes(16))
                                .setSender(recordItemBuilder.bytes(20))
                                .setTargetApplication(recordItemBuilder.bytes(20))))
                .setRunningHashAfterProcessing(recordItemBuilder.bytes(32))
                .build();
        final var patchedConnectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(DomainUtils.fromBytes(patchedConnectorId))
                .build();
        final var patchedConnectorValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setLockedStake(90_000_000L)
                .setSlashCount(1)
                .setInFlightMessageCount(1)
                .build();
        final var removedConnectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(DomainUtils.fromBytes(removedConnectorId))
                .build();

        final var recordItem = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new StateChangeContext.ClprChannelEntry(
                                channelId, clprChannelMapUpdate(channelId, channelValue))))
                        .clprConnectors(List.of(new StateChangeContext.ClprConnectorEntry(
                                patchedConnectorKey,
                                clprConnectorMapUpdate(patchedConnectorKey, patchedConnectorValue))))
                        .clprConnectorsRemoved(List.of(removedConnectorKey))
                        .clprMessages(List.of(new StateChangeContext.ClprMessageEntry(messageKey, messageValue))))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.findById(DomainUtils.toBytes(channelId)))
                .get()
                .returns(2L, ClprChannel::getNextMessageId)
                .returns(ClprChannelStatus.ACTIVE_VALUE, ClprChannel::getStatus);
        softly.assertThat(clprMessageRepository.findAll())
                .hasSize(1)
                .first()
                .returns(DomainUtils.toBytes(channelId), ClprMessage::getChannelId)
                .returns(1L, ClprMessage::getMessageId)
                .returns(patchedConnectorId, ClprMessage::getConnectorId);
        softly.assertThat(clprConnectorRepository.findById(
                        new AbstractClprConnector.Id(DomainUtils.toBytes(channelId), patchedConnectorId)))
                .get()
                .returns(90_000_000L, ClprConnector::getLockedStake)
                .returns(1L, ClprConnector::getSlashCount)
                .returns(1L, ClprConnector::getInFlightMessageCount)
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus);
        softly.assertThat(clprConnectorRepository.findById(
                        new AbstractClprConnector.Id(DomainUtils.toBytes(channelId), removedConnectorId)))
                .get()
                .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus);
    }

    @Test
    void clprSubmitBundlePeerConfigTimestampHistorizes() {
        // given -- the live channel row must already exist. peer_config_timestamp/peer_throttles track the peer's
        // ledger configuration (consensus enforces strictly increasing config updates per channel), not per-message
        // traffic, so a partial update touching either must still historize the prior channel version.
        final var completeRecordItem = recordItemBuilder.clprCreateChannel().build();
        parseRecordItemAndCommit(completeRecordItem);
        final var channelId =
                completeRecordItem.getTransactionBody().getClprCreateChannel().getChannelId();

        final var firstPeerConfigTimestamp =
                Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(1).build();
        final var firstChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setPeerConfigTimestamp(firstPeerConfigTimestamp)
                .setPeerThrottles(
                        ClprThrottles.newBuilder().setMaxMessagesPerBundle(10).build())
                .build();
        final var firstRecordItem = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new StateChangeContext.ClprChannelEntry(
                                channelId, partialClprChannelMapUpdate(channelId, firstChannelValue))))
                        .clprConnectors(List.of())
                        .clprConnectorsRemoved(List.of())
                        .clprMessages(List.of()))
                .build();

        // when -- first update sets both peer fields
        parseRecordItemAndCommit(firstRecordItem);

        // then
        softly.assertThat(clprChannelRepository.findById(DomainUtils.toBytes(channelId)))
                .get()
                .returns(DomainUtils.timestampInNanosMax(firstPeerConfigTimestamp), ClprChannel::getPeerConfigTimestamp)
                .satisfies(
                        channel -> softly.assertThat(channel.getPeerThrottle().getMaxMessagesPerBundle())
                                .isEqualTo(10L));
        softly.assertThat(findHistory(ClprChannel.class))
                .singleElement()
                .returns(
                        Range.closedOpen(
                                completeRecordItem.getConsensusTimestamp(), firstRecordItem.getConsensusTimestamp()),
                        ClprChannel::getTimestampRange);

        // given -- second update touches only peer_config_timestamp; peer_throttles must carry forward unchanged
        final var secondPeerConfigTimestamp =
                Timestamp.newBuilder().setSeconds(1_700_000_100L).setNanos(2).build();
        final var secondChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setPeerConfigTimestamp(secondPeerConfigTimestamp)
                .build();
        final var secondRecordItem = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new StateChangeContext.ClprChannelEntry(
                                channelId, partialClprChannelMapUpdate(channelId, secondChannelValue))))
                        .clprConnectors(List.of())
                        .clprConnectorsRemoved(List.of())
                        .clprMessages(List.of()))
                .build();

        // when
        parseRecordItemAndCommit(secondRecordItem);

        // then -- peer_config_timestamp advances, peer_throttles is carried forward unchanged, and the first
        // version historizes
        softly.assertThat(clprChannelRepository.findById(DomainUtils.toBytes(channelId)))
                .get()
                .returns(
                        DomainUtils.timestampInNanosMax(secondPeerConfigTimestamp), ClprChannel::getPeerConfigTimestamp)
                .satisfies(
                        channel -> softly.assertThat(channel.getPeerThrottle().getMaxMessagesPerBundle())
                                .isEqualTo(10L));
        softly.assertThat(findHistory(ClprChannel.class))
                .hasSize(2)
                .anySatisfy(channel -> softly.assertThat(channel)
                        .returns(
                                Range.closedOpen(
                                        firstRecordItem.getConsensusTimestamp(),
                                        secondRecordItem.getConsensusTimestamp()),
                                ClprChannel::getTimestampRange));
    }

    @Test
    void clprSubmitBundleOrderingViolationPausesActiveChannel() {
        // Model the block-stream outcome of an out-of-order reply on an ACTIVE channel: consensus pauses
        // the channel without enqueueing a message. The importer does not validate the bundle payload.
        final var completeRecordItem = recordItemBuilder.clprCreateChannel().build();
        parseRecordItemAndCommit(completeRecordItem);
        final var channelId =
                completeRecordItem.getTransactionBody().getClprCreateChannel().getChannelId();
        final var existingMessage = domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(DomainUtils.toBytes(channelId)).messageId(1L))
                .persist();

        final var pausedValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setStatus(ClprChannelStatus.PAUSED)
                .build();
        final var recordItem = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new StateChangeContext.ClprChannelEntry(
                                channelId, partialClprChannelMapUpdate(channelId, pausedValue))))
                        .clprConnectors(List.of())
                        .clprConnectorsRemoved(List.of())
                        .clprMessages(List.of()))
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.findById(DomainUtils.toBytes(channelId)))
                .get()
                .returns(ClprChannelStatus.PAUSED_VALUE, ClprChannel::getStatus)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprChannel::getTimestampRange)
                .returns(completeRecordItem.getConsensusTimestamp(), ClprChannel::getCreatedTimestamp);
        softly.assertThat(findHistory(ClprChannel.class))
                .singleElement()
                .returns(ClprChannelStatus.ACTIVE_VALUE, ClprChannel::getStatus)
                .returns(
                        Range.closedOpen(
                                completeRecordItem.getConsensusTimestamp(), recordItem.getConsensusTimestamp()),
                        ClprChannel::getTimestampRange);
        softly.assertThat(clprMessageRepository.findAll())
                .singleElement()
                .returns(existingMessage.getChannelId(), ClprMessage::getChannelId)
                .returns(existingMessage.getMessageId(), ClprMessage::getMessageId)
                .returns(existingMessage.getMessageData(), ClprMessage::getMessageData);
        softly.assertThat(clprConnectorRepository.count()).isZero();
    }

    @Test
    void clprSubmitBundleOrderingViolationOnPausedChannelHasNoStateChanges() {
        // Model an out-of-order reply on an already PAUSED channel without manifest/trust-anchor changes.
        // Consensus returns successfully without writing CLPR state; the inbound bundle is not an outbound
        // queue entry, and the existing message must remain intact.
        final var pausedChannel = domainBuilder
                .clprChannel()
                .customize(c -> c.status(ClprChannelStatus.PAUSED_VALUE))
                .persist();
        final var existingMessage = domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(pausedChannel.getChannelId()).messageId(1L))
                .persist();
        final var recordItem = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(DomainUtils.fromBytes(pausedChannel.getChannelId())))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of())
                        .clprConnectors(List.of())
                        .clprConnectorsRemoved(List.of())
                        .clprMessages(List.of()))
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.findById(pausedChannel.getChannelId()))
                .get()
                .returns(ClprChannelStatus.PAUSED_VALUE, ClprChannel::getStatus)
                .returns(pausedChannel.getTimestampRange(), ClprChannel::getTimestampRange);
        softly.assertThat(findHistory(ClprChannel.class)).isEmpty();
        softly.assertThat(clprMessageRepository.findAll())
                .singleElement()
                .returns(existingMessage.getChannelId(), ClprMessage::getChannelId)
                .returns(existingMessage.getMessageId(), ClprMessage::getMessageId)
                .returns(existingMessage.getMessageData(), ClprMessage::getMessageData);
        softly.assertThat(clprConnectorRepository.count()).isZero();
        softly.assertThat(clprEndpointManifestRepository.findLatest()).isNull();
        softly.assertThat(clprLedgerConfigurationRepository.findLatest()).isNull();
        softly.assertThat(clprMaintenanceRepository.count()).isZero();
    }

    @Test
    void clprSubmitBundleInvalidTransaction() {
        final var recordItem = recordItemBuilder
                .clprSubmitBundle()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.count()).isZero();
        softly.assertThat(clprMessageRepository.count()).isZero();
        softly.assertThat(clprConnectorRepository.count()).isZero();
    }

    @Test
    void clprMaintenance() {
        final var recordItem = recordItemBuilder.clprMaintenance().build();
        final var manifestValue =
                recordItem.getClprContext().getClprEndpointManifestUpdate().getClprEndpointManifestValue();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprMaintenanceRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), ClprMaintenance::getConsensusTimestamp)
                .returns(ENDPOINT_MANIFEST_RECONCILIATION_VALUE, ClprMaintenance::getReason);
        softly.assertThat(clprEndpointManifestRepository.findLatest())
                .isNotNull()
                .returns(recordItem.getConsensusTimestamp(), ClprEndpointManifest::getConsensusTimestamp)
                .returns(
                        DomainUtils.toBytes(manifestValue.getServiceAddress()), ClprEndpointManifest::getServiceAddress)
                .returns(manifestValue.getVersion(), ClprEndpointManifest::getVersion);
    }

    @Test
    void clprMaintenanceLedgerConfiguration() {
        final var recordItem =
                recordItemBuilder.clprMaintenanceLedgerConfiguration().build();
        final var configValue =
                recordItem.getClprContext().getClprLedgerConfigurationUpdate().getClprLedgerConfigurationValue();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprMaintenanceRepository.findAll())
                .hasSize(1)
                .first()
                .returns(GENESIS_INITIALIZATION_VALUE, ClprMaintenance::getReason);
        softly.assertThat(clprLedgerConfigurationRepository.findLatest())
                .isNotNull()
                .returns(recordItem.getConsensusTimestamp(), ClprLedgerConfiguration::getConsensusTimestamp)
                .returns(configValue.getChainId(), ClprLedgerConfiguration::getChainId)
                .returns(
                        Integer.toUnsignedLong(configValue.getProtocolVersion()),
                        ClprLedgerConfiguration::getProtocolVersion);
    }

    @Test
    void clprMaintenanceInvalidTransaction() {
        final var recordItem = recordItemBuilder
                .clprMaintenance()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprMaintenanceRepository.count()).isZero();
        softly.assertThat(clprEndpointManifestRepository.findLatest()).isNull();
    }

    @Test
    void clprUpdateLedgerConfiguration() {
        final var recordItem = recordItemBuilder.clprUpdateLedgerConfiguration().build();
        final var configValue = recordItem
                .getTransactionBody()
                .getClprUpdateLedgerConfiguration()
                .getConfiguration();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprLedgerConfigurationRepository.findLatest())
                .isNotNull()
                .returns(recordItem.getConsensusTimestamp(), ClprLedgerConfiguration::getConsensusTimestamp)
                .returns(configValue.getChainId(), ClprLedgerConfiguration::getChainId)
                .returns(
                        Integer.toUnsignedLong(configValue.getProtocolVersion()),
                        ClprLedgerConfiguration::getProtocolVersion)
                .returns(
                        DomainUtils.toBytes(configValue.getServiceAddress()),
                        ClprLedgerConfiguration::getServiceAddress)
                .returns(
                        DomainUtils.toBytes(configValue.getInitialTrustAnchor()),
                        ClprLedgerConfiguration::getInitialTrustAnchor)
                .returns(
                        DomainUtils.toBytes(configValue.getInitialTrustAnchorId()),
                        ClprLedgerConfiguration::getInitialTrustAnchorId);
    }

    @Test
    void clprUpdateLedgerConfigurationInvalidTransaction() {
        final var recordItem = recordItemBuilder
                .clprUpdateLedgerConfiguration()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprLedgerConfigurationRepository.findLatest()).isNull();
    }

    @Test
    void clprDoNotPersist() {
        // given - clpr objects are not persisted
        entityProperties.getPersist().setClpr(false);

        final var deleteChannelRecordItem =
                recordItemBuilder.clprDeleteChannel().build();
        final var createChannelRecordItem =
                recordItemBuilder.clprCreateChannel().build();
        final var deleteConnectorRecordItem =
                recordItemBuilder.clprDeleteConnector().build();
        final var endpointManifestRecordItem = recordItemBuilder
                .clprMaintenanceEndpointManifestAndLedgerConfiguration()
                .build();
        final var ledgerConfigRecordItem =
                recordItemBuilder.clprMaintenanceLedgerConfiguration().build();
        final var maintenanceRecordItem = recordItemBuilder.clprMaintenance().build();
        final var createConnectorRecordItem =
                recordItemBuilder.clprCreateConnector().build();
        final var submitBundleRecordItem = recordItemBuilder.clprSubmitBundle().build();

        // when
        parseRecordItemsAndCommit(List.of(
                deleteChannelRecordItem,
                createChannelRecordItem,
                deleteConnectorRecordItem,
                endpointManifestRecordItem,
                ledgerConfigRecordItem,
                maintenanceRecordItem,
                createConnectorRecordItem,
                submitBundleRecordItem));

        // then
        assertTransactionAndRecord(
                deleteChannelRecordItem.getTransactionBody(), deleteChannelRecordItem.getTransactionRecord());
        assertTransactionAndRecord(
                createChannelRecordItem.getTransactionBody(), createChannelRecordItem.getTransactionRecord());
        assertTransactionAndRecord(
                deleteConnectorRecordItem.getTransactionBody(), deleteConnectorRecordItem.getTransactionRecord());
        assertTransactionAndRecord(
                endpointManifestRecordItem.getTransactionBody(), endpointManifestRecordItem.getTransactionRecord());
        assertTransactionAndRecord(
                ledgerConfigRecordItem.getTransactionBody(), ledgerConfigRecordItem.getTransactionRecord());
        assertTransactionAndRecord(
                maintenanceRecordItem.getTransactionBody(), maintenanceRecordItem.getTransactionRecord());
        assertTransactionAndRecord(
                createConnectorRecordItem.getTransactionBody(), createConnectorRecordItem.getTransactionRecord());
        assertTransactionAndRecord(
                submitBundleRecordItem.getTransactionBody(), submitBundleRecordItem.getTransactionRecord());

        softly.assertThat(clprChannelRepository.count()).isZero();
        softly.assertThat(clprConnectorRepository.count()).isZero();
        softly.assertThat(clprEndpointManifestRepository.findLatest()).isNull();
        softly.assertThat(clprLedgerConfigurationRepository.findLatest()).isNull();
        softly.assertThat(clprMaintenanceRepository.count()).isZero();
        softly.assertThat(clprMessageRepository.count()).isZero();
    }

    private static MapUpdateChange partialClprChannelMapUpdate(
            ByteString channelId, com.hederahashgraph.api.proto.java.ClprChannel value) {
        return MapUpdateChange.newBuilder()
                .setPartial(true)
                .setKey(MapChangeKey.newBuilder().setProtoBytesKey(BytesValue.of(channelId)))
                .setValue(MapChangeValue.newBuilder().setClprChannelValue(value))
                .build();
    }
}
