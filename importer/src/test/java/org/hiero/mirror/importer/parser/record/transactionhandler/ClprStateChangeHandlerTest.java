// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprChannelMapUpdate;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.clprConnectorMapUpdate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
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
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprChannelEntry;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprConnectorEntry;
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
        when(clprContext.getClprChannels())
                .thenReturn(List.of(new ClprChannelEntry(
                        DomainUtils.fromBytes(channelId), clprChannelMapUpdate(clprChannelValue))));

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
        when(clprContext.getClprChannels())
                .thenReturn(List.of(new ClprChannelEntry(
                        DomainUtils.fromBytes(channelId), clprChannelMapUpdate(clprChannelValue))));

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
    void processChannelPendingStatus() {
        // given -- PENDING is pre-creation only (tracked via ClprChannelPendingCommitment); an existing ClprChannel
        // state-change value reverting to it is a distinct invariant violation from a truly unknown status
        mockEmptyClprState();
        final var channelId = domainBuilder.bytes(32);
        final var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(DomainUtils.fromBytes(channelId))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.PENDING)
                .build();
        when(clprContext.getClprChannels())
                .thenReturn(List.of(new ClprChannelEntry(
                        DomainUtils.fromBytes(channelId), clprChannelMapUpdate(clprChannelValue))));

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
        when(clprContext.getClprChannels())
                .thenReturn(List.of(new ClprChannelEntry(
                        DomainUtils.fromBytes(channelId), clprChannelMapUpdate(clprChannelValue))));

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
    void processChannelFullUpdateAlwaysHistorizes() {
        // given -- a full update (partial=false) can't tell us which field actually changed, so it must always
        // historize even when only counter-type fields (next_message_id, acked_message_id) are set
        mockEmptyClprState();
        final var channelId = domainBuilder.bytes(32);
        long consensusTimestamp = domainBuilder.timestamp();
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);
        final var fullValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(DomainUtils.fromBytes(channelId))
                .setNextMessageId(6)
                .setAckedMessageId(3)
                .build();
        final var mapUpdate = MapUpdateChange.newBuilder()
                .setValue(MapChangeValue.newBuilder().setClprChannelValue(fullValue))
                .build();
        when(clprContext.getClprChannels())
                .thenReturn(List.of(new ClprChannelEntry(DomainUtils.fromBytes(channelId), mapUpdate)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue()).returns(Range.atLeast(consensusTimestamp), ClprChannel::getTimestampRange);
    }

    @Test
    void processChannelPartialCounterOnlyDoesNotHistorizeAndLeavesOtherFieldsAbsent() {
        // given -- a partial update touching only counter-type fields (next_message_id, acked_message_id) must not
        // historize, and every field absent from the partial must be left unset so the merge/upsert layer carries
        // the existing value forward instead of overwriting it with a default
        mockEmptyClprState();
        final var channelId = domainBuilder.bytes(32);
        final var partialValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setNextMessageId(6)
                .setAckedMessageId(3)
                .build();
        final var mapUpdate = MapUpdateChange.newBuilder()
                .setPartial(true)
                .setValue(MapChangeValue.newBuilder().setClprChannelValue(partialValue))
                .build();
        when(clprContext.getClprChannels())
                .thenReturn(List.of(new ClprChannelEntry(DomainUtils.fromBytes(channelId), mapUpdate)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue())
                .returns(6L, ClprChannel::getNextMessageId)
                .returns(3L, ClprChannel::getAckedMessageId)
                .returns(null, ClprChannel::getStatus)
                .returns(null, ClprChannel::getTimestampRange);
    }

    @Test
    void processChannelPartialStatusChangeHistorizes() {
        // given -- a partial update touching status (not a counter field) must historize
        mockEmptyClprState();
        final var channelId = domainBuilder.bytes(32);
        long consensusTimestamp = domainBuilder.timestamp();
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);
        final var partialValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.PAUSED)
                .build();
        final var mapUpdate = MapUpdateChange.newBuilder()
                .setPartial(true)
                .setValue(MapChangeValue.newBuilder().setClprChannelValue(partialValue))
                .build();
        when(clprContext.getClprChannels())
                .thenReturn(List.of(new ClprChannelEntry(DomainUtils.fromBytes(channelId), mapUpdate)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue()).returns(Range.atLeast(consensusTimestamp), ClprChannel::getTimestampRange);
    }

    @Test
    void processChannelPartialClearedFieldHistorizesWithDefaultValue() {
        // given -- trust_anchor explicitly cleared (not just omitted) in an otherwise counter-only partial update
        // must historize, and the cleared field must be set to its proto default rather than left absent
        mockEmptyClprState();
        final var channelId = domainBuilder.bytes(32);
        long consensusTimestamp = domainBuilder.timestamp();
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);
        final var partialValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setNextMessageId(7)
                .build();
        final var mapUpdate = MapUpdateChange.newBuilder()
                .setPartial(true)
                .addClearedFields(com.hederahashgraph.api.proto.java.ClprChannel.TRUST_ANCHOR_FIELD_NUMBER)
                .setValue(MapChangeValue.newBuilder().setClprChannelValue(partialValue))
                .build();
        when(clprContext.getClprChannels())
                .thenReturn(List.of(new ClprChannelEntry(DomainUtils.fromBytes(channelId), mapUpdate)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue())
                .returns(7L, ClprChannel::getNextMessageId)
                .returns(new byte[0], ClprChannel::getTrustAnchor)
                .returns(Range.atLeast(consensusTimestamp), ClprChannel::getTimestampRange);
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
        when(clprContext.getClprChannels())
                .thenReturn(List.of(
                        new ClprChannelEntry(
                                DomainUtils.fromBytes(channelId1), clprChannelMapUpdate(clprChannelValue1)),
                        new ClprChannelEntry(
                                DomainUtils.fromBytes(channelId2), clprChannelMapUpdate(clprChannelValue2))));

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
    void processMessageControlUnexpectedPayloadCase() {
        // given -- CONTROL payload set, but its own nested oneof is unset (future protocol value); must be
        // skipped rather than persisted with an empty control message
        mockEmptyClprState();
        final var key = ClprMessageKey.newBuilder()
                .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setMessageId(1)
                .build();
        final var value = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setPayload(ClprMessagePayload.newBuilder().setControl(ClprControlMessage.newBuilder()))
                .build();
        when(clprContext.getClprMessages()).thenReturn(List.of(new ClprMessageEntry(key, value)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        verifyNoInteractions(entityListener);
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
        // given -- an update for one connector and a removal for a different connector in the same transaction.
        // A single connector key can never appear in both getClprConnectors() and getClprConnectorsRemoved() for
        // the same transaction -- consensus-node collapses same-key put/remove pairs into a single net state
        // change before StateChanges is ever populated (see CLPR_QA.md).
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
        final var patchedConnectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(patchedConnectorId)
                .build();

        final var removedConnectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var removedConnectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(removedConnectorId)
                .build();

        final var patchedCommitment = domainBuilder.bytes(32);
        final var removedCommitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(patchedConnectorId)))
                .thenReturn(patchedCommitment);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(removedConnectorId)))
                .thenReturn(removedCommitment);

        when(clprContext.getClprConnectors())
                .thenReturn(List.of(
                        new ClprConnectorEntry(patchedConnectorKey, clprConnectorMapUpdate(patchedConnectorValue))));
        when(clprContext.getClprConnectorsRemoved()).thenReturn(List.of(removedConnectorKey));

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
                        .returns(Range.atLeast(consensusTimestamp), ClprConnector::getTimestampRange)
                        .returns(null, ClprConnector::getStatus))
                .anySatisfy(connector -> assertThat(connector)
                        .returns(DomainUtils.toBytes(channelId), ClprConnector::getChannelId)
                        .returns(removedCommitment, ClprConnector::getCommitment)
                        .returns(DomainUtils.toBytes(removedConnectorId), ClprConnector::getConnectorId)
                        .returns(null, ClprConnector::getLockedStake)
                        .returns(null, ClprConnector::getSlashCount)
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
        final var connectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();
        final var commitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .thenReturn(commitment);
        when(clprContext.getClprConnectors())
                .thenReturn(List.of(new ClprConnectorEntry(connectorKey, clprConnectorMapUpdate(connectorValue))));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue()).returns(4_294_967_295L, ClprConnector::getSlashCount);
    }

    @Test
    void processConnectorFullUpdateAlwaysHistorizes() {
        // given -- a full update (partial=false) can't tell us which field actually changed, so it must always
        // historize even when only counter-type fields (locked_stake, in_flight_message_count) are set
        mockEmptyClprState();
        long consensusTimestamp = domainBuilder.timestamp();
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);
        final var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var fullValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .setLockedStake(80_000_000L)
                .setInFlightMessageCount(2)
                .build();
        final var connectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();
        final var commitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .thenReturn(commitment);
        final var mapUpdate = MapUpdateChange.newBuilder()
                .setValue(MapChangeValue.newBuilder().setClprConnectorValue(fullValue))
                .build();
        when(clprContext.getClprConnectors()).thenReturn(List.of(new ClprConnectorEntry(connectorKey, mapUpdate)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue())
                .returns(80_000_000L, ClprConnector::getLockedStake)
                .returns(Range.atLeast(consensusTimestamp), ClprConnector::getTimestampRange);
    }

    @Test
    void processConnectorPartialCounterOnlyDoesNotHistorizeAndLeavesOtherFieldsAbsent() {
        // given -- a partial update touching only counter-type fields (locked_stake, in_flight_message_count) must
        // not historize, and admin_key -- absent from the partial -- must be left unset
        mockEmptyClprState();
        final var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var partialValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setLockedStake(80_000_000L)
                .setInFlightMessageCount(2)
                .build();
        final var connectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();
        final var commitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .thenReturn(commitment);
        final var mapUpdate = MapUpdateChange.newBuilder()
                .setPartial(true)
                .setValue(MapChangeValue.newBuilder().setClprConnectorValue(partialValue))
                .build();
        when(clprContext.getClprConnectors()).thenReturn(List.of(new ClprConnectorEntry(connectorKey, mapUpdate)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue())
                .returns(80_000_000L, ClprConnector::getLockedStake)
                .returns(2L, ClprConnector::getInFlightMessageCount)
                .returns(null, ClprConnector::getAdminKey)
                .returns(null, ClprConnector::getTimestampRange);
    }

    @Test
    void processConnectorPartialAdminKeyChangeHistorizes() {
        // given -- a partial update touching admin_key (not a counter field) must historize
        mockEmptyClprState();
        long consensusTimestamp = domainBuilder.timestamp();
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);
        final var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        final var adminKey = Key.newBuilder()
                .setEd25519(DomainUtils.fromBytes(domainBuilder.key()))
                .build();
        final var partialValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setAdminKey(adminKey)
                .build();
        final var connectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();
        final var commitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .thenReturn(commitment);
        final var mapUpdate = MapUpdateChange.newBuilder()
                .setPartial(true)
                .setValue(MapChangeValue.newBuilder().setClprConnectorValue(partialValue))
                .build();
        when(clprContext.getClprConnectors()).thenReturn(List.of(new ClprConnectorEntry(connectorKey, mapUpdate)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        final var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue())
                .returns(adminKey.toByteArray(), ClprConnector::getAdminKey)
                .returns(Range.atLeast(consensusTimestamp), ClprConnector::getTimestampRange);
    }

    @Test
    void processRemovedConnector() {
        // given -- a plain removal; mergeClprConnector backfills from prior state
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
        final var connectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(connectorId)
                .build();
        when(clprConnectorService.lookup(DomainUtils.toBytes(channelId), DomainUtils.toBytes(connectorId)))
                .thenReturn(null);
        when(clprContext.getClprConnectors())
                .thenReturn(List.of(new ClprConnectorEntry(connectorKey, clprConnectorMapUpdate(connectorValue))));

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
        final var channelEntry = realClprContext.getClprChannel();
        final var connectorEntry = realClprContext.getClprConnector();
        final var removedConnectorKey =
                realClprContext.getClprConnectorsRemoved().getFirst();
        final var connectorCommitment = domainBuilder.bytes(32);
        final var removedCommitment = domainBuilder.bytes(32);
        when(clprConnectorService.lookup(
                        DomainUtils.toBytes(connectorEntry.key().getChannelId()),
                        DomainUtils.toBytes(connectorEntry.key().getConnectorId())))
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
                .returns(DomainUtils.toBytes(channelEntry.channelId()), ClprChannel::getChannelId);

        final var messageCaptor = ArgumentCaptor.forClass(ClprMessage.class);
        verify(entityListener).onClprMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).returns(ClprMessageType.DATA, ClprMessage::getMessageType);

        final var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener, times(2)).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getAllValues())
                .hasSize(2)
                .anySatisfy(connector -> assertThat(connector)
                        .returns(
                                DomainUtils.toBytes(connectorEntry.key().getConnectorId()),
                                ClprConnector::getConnectorId)
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
