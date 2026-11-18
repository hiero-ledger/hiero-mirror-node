// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason.ENDPOINT_MANIFEST_RECONCILIATION_VALUE;
import static com.hedera.hapi.services.auxiliary.clpr.legacy.ClprMaintenanceReason.GENESIS_INITIALIZATION_VALUE;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprChannelMapUpdate;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprConnectorMapUpdate;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprChannelStatus;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import com.hederahashgraph.api.proto.java.ClprMessageKey;
import com.hederahashgraph.api.proto.java.ClprMessagePayload;
import com.hederahashgraph.api.proto.java.ClprMessageValue;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprMaintenance;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.StateChangeContext;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.ClprChannelPendingCommitmentRepository;
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
class EntityRecordItemListenerClprTest extends AbstractEntityRecordItemListenerTest {

    private final ClprChannelPendingCommitmentRepository clprChannelPendingCommitmentRepository;
    private final ClprChannelRepository clprChannelRepository;
    private final ClprConnectorRepository clprConnectorRepository;
    private final ClprEndpointManifestRepository clprEndpointManifestRepository;
    private final ClprLedgerConfigurationRepository clprLedgerConfigurationRepository;
    private final ClprMaintenanceRepository clprMaintenanceRepository;
    private final ClprMessageRepository clprMessageRepository;

    @BeforeEach
    @AfterEach
    void resetPersistProperties() {
        entityProperties.getPersist().setClprChannel(true);
        entityProperties.getPersist().setClprConnector(true);
        entityProperties.getPersist().setClprEndpointManifest(true);
        entityProperties.getPersist().setClprLedgerConfiguration(true);
        entityProperties.getPersist().setClprMaintenance(true);
        entityProperties.getPersist().setClprMessage(true);
    }

    @Test
    void clprRegisterChannel() {
        var recordItem = recordItemBuilder.clprRegisterChannel().build();
        var body = recordItem.getTransactionBody().getClprRegisterChannel();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelPendingCommitmentRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), ClprChannelPendingCommitment::getCreatedTimestamp)
                .returns(
                        DomainUtils.toBytes(body.getOwnershipCommitment()),
                        ClprChannelPendingCommitment::getOwnershipCommitment)
                .returns(ClprPendingCommitmentStatus.OPEN, ClprChannelPendingCommitment::getStatus)
                .returns(
                        Range.atLeast(recordItem.getConsensusTimestamp()),
                        ClprChannelPendingCommitment::getTimestampRange);
    }

    @Test
    void clprRegisterChannelInvalidTransaction() {
        var recordItem = recordItemBuilder
                .clprRegisterChannel()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelPendingCommitmentRepository.count()).isZero();
    }

    @Test
    void clprCompleteChannel() {
        // given -- a pending commitment row must already exist since completion only patches it (its
        // created_timestamp carries forward via coalesce) rather than inserting it fresh
        var registerRecordItem = recordItemBuilder.clprRegisterChannel().build();
        parseRecordItemAndCommit(registerRecordItem);
        var ownershipCommitment =
                registerRecordItem.getTransactionBody().getClprRegisterChannel().getOwnershipCommitment();

        var channelId = recordItemBuilder.bytes(32);
        var channelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChainId("hedera:mainnet")
                .setChannelId(channelId)
                .setOwnershipCommitment(ownershipCommitment)
                .setStatus(ClprChannelStatus.ACTIVE)
                .setVerifierContract(recordItemBuilder.contractId())
                .setVerifierFingerprint(recordItemBuilder.bytes(32))
                .build();
        var recordItem = recordItemBuilder
                .clprCompleteChannel()
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new StateChangeContext.ClprChannelEntry(
                                channelId, clprChannelMapUpdate(channelId, channelValue)))))
                .build();
        var body = recordItem.getTransactionBody().getClprCompleteChannel();

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
                .returns(DomainUtils.toBytes(body.getPublicKey()), ClprChannel::getPublicKey)
                .returns(DomainUtils.toBytes(body.getSignature()), ClprChannel::getSignature)
                .returns(body.getSignatureSchemeValue(), ClprChannel::getSignatureScheme)
                .returns(DomainUtils.toBytes(body.getConfigProofBytes()), ClprChannel::getConfigProofBytes)
                .returns(
                        DomainUtils.toBytes(body.getEndpointManifestProofBytes()),
                        ClprChannel::getEndpointManifestProofBytes)
                .returns(channelValue.getStatusValue(), ClprChannel::getStatus)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprChannel::getTimestampRange);
        softly.assertThat(clprChannelPendingCommitmentRepository.findById(DomainUtils.toBytes(ownershipCommitment)))
                .get()
                .returns(ClprPendingCommitmentStatus.COMPLETED, ClprChannelPendingCommitment::getStatus)
                .returns(registerRecordItem.getConsensusTimestamp(), ClprChannelPendingCommitment::getCreatedTimestamp);
    }

    @Test
    void clprCompleteChannelInvalidTransaction() {
        var recordItem = recordItemBuilder
                .clprCompleteChannel()
                .status(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.count()).isZero();
        softly.assertThat(clprChannelPendingCommitmentRepository.count()).isZero();
    }

    @Test
    void clprChannelDoNotPersist() {
        // given -- seed the pending commitment with persistence enabled, then disable it for the completion
        var registerRecordItem = recordItemBuilder.clprRegisterChannel().build();
        parseRecordItemAndCommit(registerRecordItem);
        var ownershipCommitment =
                registerRecordItem.getTransactionBody().getClprRegisterChannel().getOwnershipCommitment();

        entityProperties.getPersist().setClprChannel(false);
        var channelId = recordItemBuilder.bytes(32);
        var channelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(channelId)
                .setOwnershipCommitment(ownershipCommitment)
                .setStatus(ClprChannelStatus.ACTIVE)
                .setVerifierContract(recordItemBuilder.contractId())
                .build();
        var recordItem = recordItemBuilder
                .clprCompleteChannel()
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new StateChangeContext.ClprChannelEntry(
                                channelId, clprChannelMapUpdate(channelId, channelValue)))))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.count()).isZero();
        softly.assertThat(clprChannelPendingCommitmentRepository.findById(DomainUtils.toBytes(ownershipCommitment)))
                .get()
                .returns(ClprPendingCommitmentStatus.OPEN, ClprChannelPendingCommitment::getStatus);
    }

    @Test
    void clprCloseChannel() {
        // given -- a live channel row must already exist since ClprCloseChannel only ever patches counters/status
        var completeRecordItem = recordItemBuilder.clprCompleteChannel().build();
        parseRecordItemAndCommit(completeRecordItem);
        var channelId =
                completeRecordItem.getTransactionBody().getClprCompleteChannel().getChannelId();
        var channelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setAckedMessageId(4)
                .setChannelId(channelId)
                .setNextMessageId(5)
                .setReceivedMessageId(3)
                .setReceivedRunningHash(recordItemBuilder.bytes(32))
                .setSentRunningHash(recordItemBuilder.bytes(32))
                .setStatus(ClprChannelStatus.CLOSING)
                .build();
        var recordItem = recordItemBuilder
                .clprCloseChannel()
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
    void clprCloseChannelSweepsAbandonedCommitment() {
        // given -- no ClprChannel state change is present, e.g. a ClprRegisterChannel commitment was never completed
        var pendingCommitment = domainBuilder.clprChannelPendingCommitment().persist();
        var recordItem = recordItemBuilder
                .clprCloseChannel()
                .transactionBody(b ->
                        b.setOwnershipCommitment(DomainUtils.fromBytes(pendingCommitment.getOwnershipCommitment())))
                .recordItem(r -> r.clprContextBuilder().clprChannels(List.of()))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelPendingCommitmentRepository.findById(pendingCommitment.getOwnershipCommitment()))
                .get()
                .returns(ClprPendingCommitmentStatus.DELETED, ClprChannelPendingCommitment::getStatus);
        softly.assertThat(clprChannelRepository.count()).isZero();
    }

    @Test
    void clprCloseChannelInvalidTransaction() {
        var recordItem = recordItemBuilder
                .clprCloseChannel()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprChannelRepository.count()).isZero();
        softly.assertThat(clprChannelPendingCommitmentRepository.count()).isZero();
    }

    @Test
    void clprRegisterConnector() {
        var recordItem = recordItemBuilder.clprRegisterConnector().build();
        var body = recordItem.getTransactionBody().getClprRegisterConnector();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.findAll())
                .hasSize(1)
                .first()
                .returns(DomainUtils.toBytes(body.getChannelId()), ClprConnector::getChannelId)
                .returns(DomainUtils.toBytes(body.getCommitment()), ClprConnector::getCommitment)
                .returns(recordItem.getConsensusTimestamp(), ClprConnector::getCreatedTimestamp)
                .returns(ClprConnectorStatus.PENDING, ClprConnector::getStatus)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprConnector::getTimestampRange);
    }

    @Test
    void clprRegisterConnectorInvalidTransaction() {
        var recordItem = recordItemBuilder
                .clprRegisterConnector()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.count()).isZero();
    }

    @Test
    void clprConnectorDoNotPersist() {
        entityProperties.getPersist().setClprConnector(false);
        var recordItem = recordItemBuilder.clprRegisterConnector().build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.count()).isZero();
    }

    @Test
    void clprCompleteConnector() {
        var recordItem = recordItemBuilder.clprCompleteConnector().build();
        var body = recordItem.getTransactionBody().getClprCompleteConnector();
        var connectorValue = recordItem.getClprContext().getClprConnector().value();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.findByChannelIdAndConnectorId(
                        DomainUtils.toBytes(body.getChannelId()), DomainUtils.toBytes(body.getConnectorId())))
                .get()
                .returns(connectorValue.getAdminKey().toByteArray(), ClprConnector::getAdminKey)
                .returns(EntityId.of(connectorValue.getConnectorContract()), ClprConnector::getConnectorContractId)
                .returns(connectorValue.getLockedStake(), ClprConnector::getLockedStake)
                .returns(recordItem.getConsensusTimestamp(), ClprConnector::getCreatedTimestamp)
                .returns(DomainUtils.toBytes(body.getPublicKey()), ClprConnector::getPublicKey)
                .returns(DomainUtils.toBytes(body.getSalt()), ClprConnector::getSalt)
                .returns(DomainUtils.toBytes(body.getSignature()), ClprConnector::getSignature)
                .returns(body.getSignatureSchemeValue(), ClprConnector::getSignatureScheme)
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprConnector::getTimestampRange);
    }

    @Test
    void clprCompleteConnectorInvalidTransaction() {
        var recordItem = recordItemBuilder
                .clprCompleteConnector()
                .status(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.count()).isZero();
    }

    @Test
    void clprDeregisterConnector() {
        // given -- a registered/completed connector row must already exist since deregistration looks up its commitment
        var seedConnector = domainBuilder.clprConnector().persist();
        var recordItem = recordItemBuilder
                .clprDeregisterConnector()
                .transactionBody(b -> b.setChannelId(DomainUtils.fromBytes(seedConnector.getChannelId()))
                        .setConnectorId(DomainUtils.fromBytes(seedConnector.getConnectorId())))
                .build();
        var body = recordItem.getTransactionBody().getClprDeregisterConnector();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.findByChannelIdAndConnectorId(
                        seedConnector.getChannelId(), seedConnector.getConnectorId()))
                .get()
                .returns(seedConnector.getCommitment(), ClprConnector::getCommitment)
                .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus)
                .returns(EntityId.of(body.getStakeRecipient()), ClprConnector::getStakeRecipientId)
                .returns(Range.atLeast(recordItem.getConsensusTimestamp()), ClprConnector::getTimestampRange);
    }

    @Test
    void clprDeregisterConnectorInvalidTransaction() {
        var seedConnector = domainBuilder.clprConnector().persist();
        var recordItem = recordItemBuilder
                .clprDeregisterConnector()
                .transactionBody(b -> b.setChannelId(DomainUtils.fromBytes(seedConnector.getChannelId()))
                        .setConnectorId(DomainUtils.fromBytes(seedConnector.getConnectorId())))
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprConnectorRepository.findByChannelIdAndConnectorId(
                        seedConnector.getChannelId(), seedConnector.getConnectorId()))
                .get()
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus);
    }

    @Test
    void clprSubmitBundle() {
        // given -- the live channel row and both referenced connector rows must already exist
        var completeRecordItem = recordItemBuilder.clprCompleteChannel().build();
        parseRecordItemAndCommit(completeRecordItem);
        var channelId =
                completeRecordItem.getTransactionBody().getClprCompleteChannel().getChannelId();

        var patchedConnectorId = domainBuilder.bytes(32);
        domainBuilder
                .clprConnector()
                .customize(c -> c.channelId(DomainUtils.toBytes(channelId)).connectorId(patchedConnectorId))
                .persist();
        var removedConnectorId = domainBuilder.bytes(32);
        domainBuilder
                .clprConnector()
                .customize(c -> c.channelId(DomainUtils.toBytes(channelId)).connectorId(removedConnectorId))
                .persist();

        var channelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setAckedMessageId(0)
                .setChannelId(channelId)
                .setNextMessageId(2)
                .setReceivedMessageId(0)
                .setReceivedRunningHash(recordItemBuilder.bytes(32))
                .setSentRunningHash(recordItemBuilder.bytes(32))
                .setStatus(ClprChannelStatus.ACTIVE)
                .build();
        var messageKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(1)
                .build();
        var messageValue = ClprMessageValue.newBuilder()
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessage(com.hederahashgraph.api.proto.java.ClprMessage.newBuilder()
                                .setConnectorId(DomainUtils.fromBytes(patchedConnectorId))
                                .setMessageData(recordItemBuilder.bytes(16))
                                .setSender(recordItemBuilder.bytes(20))
                                .setTargetApplication(recordItemBuilder.bytes(20))))
                .setRunningHashAfterProcessing(recordItemBuilder.bytes(32))
                .build();
        var patchedConnectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(DomainUtils.fromBytes(patchedConnectorId))
                .build();
        var patchedConnectorValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setLockedStake(90_000_000L)
                .setSlashCount(1)
                .setInFlightMessageCount(1)
                .build();
        var removedConnectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(DomainUtils.fromBytes(removedConnectorId))
                .build();

        var recordItem = recordItemBuilder
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
        softly.assertThat(clprConnectorRepository.findByChannelIdAndConnectorId(
                        DomainUtils.toBytes(channelId), patchedConnectorId))
                .get()
                .returns(90_000_000L, ClprConnector::getLockedStake)
                .returns(1L, ClprConnector::getSlashCount)
                .returns(1L, ClprConnector::getInFlightMessageCount)
                .returns(ClprConnectorStatus.ACTIVE, ClprConnector::getStatus);
        softly.assertThat(clprConnectorRepository.findByChannelIdAndConnectorId(
                        DomainUtils.toBytes(channelId), removedConnectorId))
                .get()
                .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus);
    }

    @Test
    void clprSubmitBundleInvalidTransaction() {
        var recordItem = recordItemBuilder
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
    void clprMessageDoNotPersist() {
        // given -- a live channel row must already exist since ClprSubmitBundle only ever patches counters
        var completeRecordItem = recordItemBuilder.clprCompleteChannel().build();
        parseRecordItemAndCommit(completeRecordItem);
        var channelId =
                completeRecordItem.getTransactionBody().getClprCompleteChannel().getChannelId();

        entityProperties.getPersist().setClprMessage(false);
        var channelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(channelId)
                .setNextMessageId(2)
                .setStatus(ClprChannelStatus.ACTIVE)
                .build();
        var messageKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(1)
                .build();
        var messageValue = ClprMessageValue.newBuilder()
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessage(com.hederahashgraph.api.proto.java.ClprMessage.newBuilder()
                                .setConnectorId(recordItemBuilder.bytes(32))
                                .setMessageData(recordItemBuilder.bytes(16))
                                .setSender(recordItemBuilder.bytes(20))
                                .setTargetApplication(recordItemBuilder.bytes(20))))
                .setRunningHashAfterProcessing(recordItemBuilder.bytes(32))
                .build();
        var recordItem = recordItemBuilder
                .clprSubmitBundle()
                .transactionBody(b -> b.setChannelId(channelId))
                .recordItem(r -> r.clprContextBuilder()
                        .clprChannels(List.of(new StateChangeContext.ClprChannelEntry(
                                channelId, clprChannelMapUpdate(channelId, channelValue))))
                        .clprConnectors(List.of())
                        .clprConnectorsRemoved(List.of())
                        .clprMessages(List.of(new StateChangeContext.ClprMessageEntry(messageKey, messageValue))))
                .build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprMessageRepository.count()).isZero();
        softly.assertThat(clprChannelRepository.findById(DomainUtils.toBytes(channelId)))
                .get()
                .returns(2L, ClprChannel::getNextMessageId);
    }

    @Test
    void clprMaintenance() {
        var recordItem = recordItemBuilder.clprMaintenance().build();
        var manifestValue =
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
        var recordItem = recordItemBuilder.clprMaintenanceLedgerConfiguration().build();
        var configValue =
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
        var recordItem = recordItemBuilder
                .clprMaintenance()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprMaintenanceRepository.count()).isZero();
        softly.assertThat(clprEndpointManifestRepository.findLatest()).isNull();
    }

    @Test
    void clprMaintenanceDoNotPersist() {
        entityProperties.getPersist().setClprMaintenance(false);
        var recordItem = recordItemBuilder.clprMaintenance().build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprMaintenanceRepository.count()).isZero();
        // gated by a different flag, so it still persists
        softly.assertThat(clprEndpointManifestRepository.findLatest()).isNotNull();
    }

    @Test
    void clprEndpointManifestDoNotPersist() {
        entityProperties.getPersist().setClprEndpointManifest(false);
        var recordItem = recordItemBuilder.clprMaintenance().build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprEndpointManifestRepository.findLatest()).isNull();
        // gated by a different flag, so it still persists
        softly.assertThat(clprMaintenanceRepository.count()).isEqualTo(1);
    }

    @Test
    void clprUpdateLedgerConfiguration() {
        var recordItem = recordItemBuilder.clprUpdateLedgerConfiguration().build();
        var configValue = recordItem
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
        var recordItem = recordItemBuilder
                .clprUpdateLedgerConfiguration()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(clprLedgerConfigurationRepository.findLatest()).isNull();
    }
}
