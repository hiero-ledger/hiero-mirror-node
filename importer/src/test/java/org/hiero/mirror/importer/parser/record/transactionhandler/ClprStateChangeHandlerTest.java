// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprConfigUpdate;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import com.hederahashgraph.api.proto.java.ClprControlMessage;
import com.hederahashgraph.api.proto.java.ClprLedgerConfiguration;
import com.hederahashgraph.api.proto.java.ClprMessageKey;
import com.hederahashgraph.api.proto.java.ClprMessagePayload;
import com.hederahashgraph.api.proto.java.ClprMessageReply;
import com.hederahashgraph.api.proto.java.ClprMessageReplyStatus;
import com.hederahashgraph.api.proto.java.ClprMessageValue;
import com.hederahashgraph.api.proto.java.ClprThrottles;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprControlConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.domain.clpr.ClprThrottle;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.ClprContext;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprMessageEntry;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ClprStateChangeHandlerTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    @Mock
    private ClprConnectorService clprConnectorService;

    @Mock
    private ClprContext clprContext;

    @Mock
    private EntityListener entityListener;

    @Mock
    private RecordItem recordItem;

    @InjectMocks
    private ClprStateChangeHandler clprStateChangeHandler;

    @Test
    void processUnsuccessfulIsNoOp() {
        // given
        when(recordItem.isSuccessful()).thenReturn(false);

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        verifyNoInteractions(entityListener);
    }

    @Test
    void processNoClprStateIsNoOp() {
        // given -- the overwhelming majority of ContractCall/EthereumTransaction calls
        mockEmptyClprState();

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        verifyNoInteractions(entityListener);
    }

    @Test
    void processChannel() {
        // given
        mockEmptyClprState();
        final var channelId = domainBuilder.bytes(32);
        final var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(DomainUtils.fromBytes(channelId))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .setNextMessageId(4)
                .setAckedMessageId(2)
                .build();
        when(clprContext.getClprChannels()).thenReturn(List.of(clprChannelValue));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue())
                .returns(channelId, ClprChannel::getChannelId)
                .returns(4L, ClprChannel::getNextMessageId)
                .returns(2L, ClprChannel::getAckedMessageId)
                .returns(Range.atLeast(0L), ClprChannel::getTimestampRange);
    }

    @Test
    void processChannelUnknownStatus() {
        // given -- an UNRECOGNIZED status (future protocol value) should log and leave status null, not drop the row
        mockEmptyClprState();
        final var channelId = domainBuilder.bytes(32);
        final var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(DomainUtils.fromBytes(channelId))
                .setStatusValue(99)
                .build();
        when(clprContext.getClprChannels()).thenReturn(List.of(clprChannelValue));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue())
                .returns(channelId, ClprChannel::getChannelId)
                .returns(null, ClprChannel::getStatus);
    }

    @Test
    void processChannelWithPeerThrottlesAndEndpointManifest() {
        // given
        mockEmptyClprState();
        final var channelId = domainBuilder.bytes(32);
        final var endpointAccountId = domainBuilder.bytes(32);
        final var tlsCertificate = domainBuilder.bytes(64);
        final var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(DomainUtils.fromBytes(channelId))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .setPeerThrottles(ClprThrottles.newBuilder()
                        .setMaxMessagesPerBundle(10)
                        .setMaxMessagePayloadBytes(1024)
                        .setMaxGasPerMessage(100_000L)
                        .setMaxQueueDepth(50)
                        .setMaxSyncBytes(1_000_000L)
                        .setMaxLocalEndpoints(5)
                        .setMaxPeerEndpoints(5))
                .setEndpointManifest(com.hederahashgraph.api.proto.java.ClprEndpointManifest.newBuilder()
                        .addEndpoints(com.hederahashgraph.api.proto.java.ClprEndpoint.newBuilder()
                                .setServiceEndpoint(com.hederahashgraph.api.proto.java.ClprServiceEndpoint.newBuilder()
                                        .setIpAddress("10.0.0.1")
                                        .setPort(50211))
                                .setTlsCertificate(DomainUtils.fromBytes(tlsCertificate))
                                .setAccountId(DomainUtils.fromBytes(endpointAccountId))))
                .build();
        when(clprContext.getClprChannels()).thenReturn(List.of(clprChannelValue));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue())
                .satisfies(channel -> assertThat(channel.getPeerThrottle())
                        .returns(10L, ClprThrottle::getMaxMessagesPerBundle)
                        .returns(1024L, ClprThrottle::getMaxMessagePayloadBytes)
                        .returns(100_000L, ClprThrottle::getMaxGasPerMessage)
                        .returns(50L, ClprThrottle::getMaxQueueDepth)
                        .returns(1_000_000L, ClprThrottle::getMaxSyncBytes)
                        .returns(5L, ClprThrottle::getMaxLocalEndpoints)
                        .returns(5L, ClprThrottle::getMaxPeerEndpoints))
                .satisfies(channel -> assertThat(channel.getEndpointManifest())
                        .singleElement()
                        .returns(tlsCertificate, ClprEndpoint::getTlsCertificate)
                        .returns(endpointAccountId, ClprEndpoint::getAccountId));
    }

    @Test
    void processSkipsSelfProcessedType() {
        // given -- ClprCompleteChannel writes onClprChannel itself; process() must not also patch it generically
        mockEmptyClprState();
        when(recordItem.getTransactionType()).thenReturn(TransactionType.CLPRCOMPLETECHANNEL.getProtoId());

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        verifyNoInteractions(entityListener);
    }

    @Test
    void processMultipleChannels() {
        // given -- a batch/hook-aggregated parent can touch more than one channel in a single transaction
        mockEmptyClprState();
        final var channelId1 = domainBuilder.bytes(32);
        final var channelId2 = domainBuilder.bytes(32);
        final var clprChannelValue1 = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(DomainUtils.fromBytes(channelId1))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .build();
        final var clprChannelValue2 = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(DomainUtils.fromBytes(channelId2))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .build();
        when(clprContext.getClprChannels()).thenReturn(List.of(clprChannelValue1, clprChannelValue2));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener, times(2)).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getAllValues())
                .extracting(ClprChannel::getChannelId)
                .containsExactlyInAnyOrder(channelId1, channelId2);
    }

    @Test
    void processMessages() {
        // given
        mockEmptyClprState();
        final var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));

        final var dataKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(1)
                .build();
        final var dataRunningHash = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var sender = DomainUtils.fromBytes(domainBuilder.bytes(20));
        final var targetApplication = DomainUtils.fromBytes(domainBuilder.bytes(20));
        final var messageData = DomainUtils.fromBytes(domainBuilder.bytes(16));
        final var dataValue = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(dataRunningHash)
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessage(com.hederahashgraph.api.proto.java.ClprMessage.newBuilder()
                                .setConnectorId(connectorId)
                                .setSender(sender)
                                .setTargetApplication(targetApplication)
                                .setMessageData(messageData)))
                .build();

        final var replyKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(2)
                .build();
        final var replyRunningHash = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var replyData = DomainUtils.fromBytes(domainBuilder.bytes(16));
        final var replyValue = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(replyRunningHash)
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessageReply(ClprMessageReply.newBuilder()
                                .setMessageId(1)
                                .setStatus(ClprMessageReplyStatus.SUCCESS)
                                .setMessageReplyData(replyData)))
                .build();

        final var controlKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(3)
                .build();
        final var controlRunningHash = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var controlValue = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(controlRunningHash)
                .setPayload(ClprMessagePayload.newBuilder()
                        .setControl(ClprControlMessage.newBuilder()
                                .setConfigUpdate(ClprConfigUpdate.newBuilder()
                                        .setConfiguration(ClprLedgerConfiguration.newBuilder()
                                                .setProtocolVersion(1)
                                                .setChainId("hedera:mainnet")
                                                .setThrottles(ClprThrottles.newBuilder()
                                                        .setMaxMessagesPerBundle(100))))))
                .build();

        // No oneof variant set; must be skipped rather than persisted
        final var unexpectedKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(4)
                .build();
        final var unexpectedValue = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setPayload(ClprMessagePayload.newBuilder())
                .build();

        when(clprContext.getClprMessages())
                .thenReturn(List.of(
                        new ClprMessageEntry(dataKey, dataValue),
                        new ClprMessageEntry(replyKey, replyValue),
                        new ClprMessageEntry(controlKey, controlValue),
                        new ClprMessageEntry(unexpectedKey, unexpectedValue)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var messageCaptor = ArgumentCaptor.forClass(ClprMessage.class);
        verify(entityListener, times(3)).onClprMessage(messageCaptor.capture());
        final var messages = messageCaptor.getAllValues();
        assertThat(messages)
                .hasSize(3)
                .anySatisfy(message -> assertThat(message)
                        .returns(DomainUtils.toBytes(channelId), ClprMessage::getChannelId)
                        .returns(1L, ClprMessage::getMessageId)
                        .returns(ClprMessageType.DATA, ClprMessage::getMessageType)
                        .returns(DomainUtils.toBytes(connectorId), ClprMessage::getConnectorId)
                        .returns(DomainUtils.toBytes(sender), ClprMessage::getSender)
                        .returns(DomainUtils.toBytes(targetApplication), ClprMessage::getTargetApplication)
                        .returns(DomainUtils.toBytes(messageData), ClprMessage::getMessageData)
                        .returns(DomainUtils.toBytes(dataRunningHash), ClprMessage::getRunningHash))
                .anySatisfy(message -> assertThat(message)
                        .returns(2L, ClprMessage::getMessageId)
                        .returns(ClprMessageType.RESPONSE, ClprMessage::getMessageType)
                        .returns(1L, ClprMessage::getReplyToMessageId)
                        .returns(
                                org.hiero.mirror.common.domain.clpr.ClprMessageReplyStatus.SUCCESS,
                                ClprMessage::getReplyStatus)
                        .returns(DomainUtils.toBytes(replyData), ClprMessage::getMessageData)
                        .returns(null, ClprMessage::getConnectorId))
                .anySatisfy(message -> assertThat(message)
                        .returns(3L, ClprMessage::getMessageId)
                        .returns(ClprMessageType.CONTROL, ClprMessage::getMessageType)
                        .satisfies(m -> assertThat(
                                        m.getControlMessage().getConfigUpdate().getConfiguration())
                                .returns(1L, ClprControlConfiguration::getProtocolVersion)
                                .returns("hedera:mainnet", ClprControlConfiguration::getChainId)
                                .returns(
                                        100L,
                                        configuration ->
                                                configuration.getThrottle().getMaxMessagesPerBundle())));

        verify(entityListener, never()).onClprMessage(argThat(m -> m.getMessageId() == 4));
    }

    @Test
    void processMessageUnknownReplyStatus() {
        // given -- an UNRECOGNIZED reply status (future protocol value) should log and leave it null, not drop
        // the message
        mockEmptyClprState();
        final var replyKey = ClprMessageKey.newBuilder()
                .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setMessageId(1)
                .build();
        final var replyValue = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessageReply(
                                ClprMessageReply.newBuilder().setMessageId(0).setStatusValue(99)))
                .build();
        when(clprContext.getClprMessages()).thenReturn(List.of(new ClprMessageEntry(replyKey, replyValue)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var messageCaptor = ArgumentCaptor.forClass(ClprMessage.class);
        verify(entityListener).onClprMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .returns(ClprMessageType.RESPONSE, ClprMessage::getMessageType)
                .returns(null, ClprMessage::getReplyStatus);
    }

    @Test
    void processConnectors() {
        // given -- a connector banned and patched in the same transaction; the DELETED row must carry the patch's
        // fields forward rather than keep the connector's pre-transaction values.
        mockEmptyClprState();
        long consensusTimestamp = domainBuilder.timestamp();
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);

        final var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var patchedConnectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorContract = domainBuilder.entityNum(2500L).toContractID();
        final var adminKey = Key.newBuilder()
                .setEd25519(DomainUtils.fromBytes(domainBuilder.key()))
                .build();
        final var patchedConnectorValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(patchedConnectorId)
                .setConnectorContract(connectorContract)
                .setAdminKey(adminKey)
                .setLockedStake(90_000_000L)
                .setSlashCount(1)
                .setInFlightMessageCount(1)
                .build();

        final var bannedConnectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var bannedConnectorContract = domainBuilder.entityNum(2600L).toContractID();
        final var bannedAdminKey = Key.newBuilder()
                .setEd25519(DomainUtils.fromBytes(domainBuilder.key()))
                .build();
        final var bannedConnectorValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(bannedConnectorId)
                .setConnectorContract(bannedConnectorContract)
                .setAdminKey(bannedAdminKey)
                .setLockedStake(0L)
                .setSlashCount(5)
                .setInFlightMessageCount(2)
                .build();
        final var bannedConnectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(bannedConnectorId)
                .build();

        final var patchedCommitment = domainBuilder.bytes(32);
        final var bannedCommitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(patchedConnectorId)))
                .thenReturn(patchedCommitment);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(bannedConnectorId)))
                .thenReturn(bannedCommitment);

        when(clprContext.getClprConnectors()).thenReturn(List.of(patchedConnectorValue, bannedConnectorValue));
        when(clprContext.getClprConnectorsRemoved()).thenReturn(List.of(bannedConnectorKey));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener, times(2)).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getAllValues())
                .hasSize(2)
                .anySatisfy(connector -> assertThat(connector)
                        .returns(DomainUtils.toBytes(channelId), ClprConnector::getChannelId)
                        .returns(patchedCommitment, ClprConnector::getCommitment)
                        .returns(DomainUtils.toBytes(patchedConnectorId), ClprConnector::getConnectorId)
                        .returns(EntityId.of(connectorContract), ClprConnector::getConnectorContractId)
                        .returns(adminKey.toByteArray(), ClprConnector::getAdminKey)
                        .returns(90_000_000L, ClprConnector::getLockedStake)
                        .returns(1L, ClprConnector::getSlashCount)
                        .returns(1L, ClprConnector::getInFlightMessageCount)
                        .returns(null, ClprConnector::getTimestampRange)
                        .returns(null, ClprConnector::getStatus))
                .anySatisfy(connector -> assertThat(connector)
                        .returns(DomainUtils.toBytes(channelId), ClprConnector::getChannelId)
                        .returns(bannedCommitment, ClprConnector::getCommitment)
                        .returns(DomainUtils.toBytes(bannedConnectorId), ClprConnector::getConnectorId)
                        .returns(EntityId.of(bannedConnectorContract), ClprConnector::getConnectorContractId)
                        .returns(bannedAdminKey.toByteArray(), ClprConnector::getAdminKey)
                        .returns(0L, ClprConnector::getLockedStake)
                        .returns(5L, ClprConnector::getSlashCount)
                        .returns(2L, ClprConnector::getInFlightMessageCount)
                        .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus)
                        .returns(Range.atLeast(consensusTimestamp), ClprConnector::getTimestampRange));
    }

    @Test
    void processConnectorSlashCountUint32Widening() {
        // given -- -1 is uint32 max; a plain (long) cast would sign-extend it to -1 instead of 4294967295
        mockEmptyClprState();
        final var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .setSlashCount(-1)
                .build();
        final var commitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .thenReturn(commitment);
        when(clprContext.getClprConnectors()).thenReturn(List.of(connectorValue));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue()).returns(4_294_967_295L, ClprConnector::getSlashCount);
    }

    @Test
    void processRemovedConnectorWithoutSameTransactionPatch() {
        // given -- a plain removal with no same-transaction patch; mergeClprConnector backfills from prior state
        mockEmptyClprState();
        long consensusTimestamp = domainBuilder.timestamp();
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);

        final var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var removedKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();
        final var commitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .thenReturn(commitment);
        when(clprContext.getClprConnectorsRemoved()).thenReturn(List.of(removedKey));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue())
                .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus)
                .returns(null, ClprConnector::getLockedStake)
                .returns(null, ClprConnector::getSlashCount)
                .returns(null, ClprConnector::getConnectorContractId);
    }

    @Test
    void processConnectorMissingCommitmentDropped() {
        // given -- the commitment lookup misses (e.g. not yet flushed in this batch), so the connector is dropped
        mockEmptyClprState();
        final var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .thenReturn(null);
        when(clprContext.getClprConnectors()).thenReturn(List.of(connectorValue));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        verify(entityListener, never()).onClprConnector(any());
    }

    @Test
    void processRemovedConnectorMissingCommitmentDropped() {
        // given -- the commitment lookup misses, so the removal is dropped rather than persisted with a null key
        mockEmptyClprState();
        final var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var removedKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .thenReturn(null);
        when(clprContext.getClprConnectorsRemoved()).thenReturn(List.of(removedKey));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        verify(entityListener, never()).onClprConnector(any());
    }

    @Test
    void processRealClprSubmitBundle() {
        // given -- exercises the real ClprContext RecordItemBuilder.clprSubmitBundle() builds, not mocks
        final var realRecordItem = recordItemBuilder.clprSubmitBundle().build();
        final var realClprContext = realRecordItem.getClprContext();
        final var channelValue = realClprContext.getClprChannel();
        final var connectorValue = realClprContext.getClprConnector();
        final var removedConnectorKey =
                realClprContext.getClprConnectorsRemoved().getFirst();
        final var connectorCommitment = domainBuilder.bytes(32);
        final var removedCommitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(
                        DomainUtils.toBytes(connectorValue.getChannelId()),
                        DomainUtils.toBytes(connectorValue.getConnectorId())))
                .thenReturn(connectorCommitment);
        when(clprConnectorService.lookup(
                        DomainUtils.toBytes(removedConnectorKey.getChannelId()),
                        DomainUtils.toBytes(removedConnectorKey.getConnectorId())))
                .thenReturn(removedCommitment);

        // when
        clprStateChangeHandler.process(realRecordItem);

        // then
        final var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue())
                .returns(DomainUtils.toBytes(channelValue.getChannelId()), ClprChannel::getChannelId);

        final var messageCaptor = ArgumentCaptor.forClass(ClprMessage.class);
        verify(entityListener).onClprMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).returns(ClprMessageType.DATA, ClprMessage::getMessageType);

        final var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener, times(2)).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getAllValues())
                .hasSize(2)
                .anySatisfy(connector -> assertThat(connector)
                        .returns(DomainUtils.toBytes(connectorValue.getConnectorId()), ClprConnector::getConnectorId)
                        .returns(connectorCommitment, ClprConnector::getCommitment)
                        .returns(null, ClprConnector::getStatus))
                .anySatisfy(connector -> assertThat(connector)
                        .returns(
                                DomainUtils.toBytes(removedConnectorKey.getConnectorId()),
                                ClprConnector::getConnectorId)
                        .returns(removedCommitment, ClprConnector::getCommitment)
                        .returns(ClprConnectorStatus.DELETED, ClprConnector::getStatus));
    }

    private void mockEmptyClprState() {
        lenient().when(recordItem.isSuccessful()).thenReturn(true);
        lenient().when(recordItem.getClprContext()).thenReturn(clprContext);
        lenient().when(clprContext.getClprChannels()).thenReturn(List.of());
        lenient().when(clprContext.getClprMessages()).thenReturn(List.of());
        lenient().when(clprContext.getClprConnectors()).thenReturn(List.of());
        lenient().when(clprContext.getClprConnectorsRemoved()).thenReturn(List.of());
    }
}
