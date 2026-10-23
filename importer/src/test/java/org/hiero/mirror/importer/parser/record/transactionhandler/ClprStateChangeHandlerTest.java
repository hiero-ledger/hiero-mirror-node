// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprControlConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprMessageEntry;
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
        var channelId = domainBuilder.bytes(32);
        var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(DomainUtils.fromBytes(channelId))
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .setNextMessageId(4)
                .setAckedMessageId(2)
                .build();
        when(recordItem.getClprChannel()).thenReturn(clprChannelValue);

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue())
                .returns(channelId, ClprChannel::getChannelId)
                .returns(4L, ClprChannel::getNextMessageId)
                .returns(2L, ClprChannel::getAckedMessageId)
                .returns(Range.atLeast(0L), ClprChannel::getTimestampRange);
    }

    @Test
    void processMessages() {
        // given
        mockEmptyClprState();
        var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));

        var dataKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(1)
                .build();
        var dataRunningHash = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var connectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var sender = DomainUtils.fromBytes(domainBuilder.bytes(20));
        var targetApplication = DomainUtils.fromBytes(domainBuilder.bytes(20));
        var messageData = DomainUtils.fromBytes(domainBuilder.bytes(16));
        var dataValue = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(dataRunningHash)
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessage(com.hederahashgraph.api.proto.java.ClprMessage.newBuilder()
                                .setConnectorId(connectorId)
                                .setSender(sender)
                                .setTargetApplication(targetApplication)
                                .setMessageData(messageData)))
                .build();

        var replyKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(2)
                .build();
        var replyRunningHash = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var replyData = DomainUtils.fromBytes(domainBuilder.bytes(16));
        var replyValue = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(replyRunningHash)
                .setPayload(ClprMessagePayload.newBuilder()
                        .setMessageReply(ClprMessageReply.newBuilder()
                                .setMessageId(1)
                                .setStatus(ClprMessageReplyStatus.SUCCESS)
                                .setMessageReplyData(replyData)))
                .build();

        var controlKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(3)
                .build();
        var controlRunningHash = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var controlValue = ClprMessageValue.newBuilder()
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
        var unexpectedKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(4)
                .build();
        var unexpectedValue = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setPayload(ClprMessagePayload.newBuilder())
                .build();

        when(recordItem.getClprMessages())
                .thenReturn(List.of(
                        new ClprMessageEntry(dataKey, dataValue),
                        new ClprMessageEntry(replyKey, replyValue),
                        new ClprMessageEntry(controlKey, controlValue),
                        new ClprMessageEntry(unexpectedKey, unexpectedValue)));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        var messageCaptor = ArgumentCaptor.forClass(ClprMessage.class);
        verify(entityListener, times(3)).onClprMessage(messageCaptor.capture());
        var messages = messageCaptor.getAllValues();
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
                                .returns(100L, ClprControlConfiguration::getMaxMessagesPerBundle)));

        verify(entityListener, never()).onClprMessage(argThat(m -> m.getMessageId() == 4));
    }

    @Test
    void processConnectors() {
        // given -- one connector patched, one banned (also present in the raw update list to prove removal wins)
        mockEmptyClprState();
        long consensusTimestamp = domainBuilder.timestamp();
        when(recordItem.getConsensusTimestamp()).thenReturn(consensusTimestamp);

        var channelId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var patchedConnectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var connectorContract = domainBuilder.entityNum(2500L).toContractID();
        var adminKey = Key.newBuilder()
                .setEd25519(DomainUtils.fromBytes(domainBuilder.key()))
                .build();
        var patchedConnectorValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(patchedConnectorId)
                .setConnectorContract(connectorContract)
                .setAdminKey(adminKey)
                .setLockedStake(90_000_000L)
                .setSlashCount(1)
                .setInFlightMessageCount(1)
                .build();

        var bannedConnectorId = DomainUtils.fromBytes(domainBuilder.bytes(32));
        var bannedConnectorValue = com.hederahashgraph.api.proto.java.ClprConnector.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(bannedConnectorId)
                .setLockedStake(0L)
                .setSlashCount(5)
                .build();
        var bannedConnectorKey = ClprConnectorKey.newBuilder()
                .setChannelId(channelId)
                .setConnectorId(bannedConnectorId)
                .build();

        when(recordItem.getClprConnectors()).thenReturn(List.of(patchedConnectorValue, bannedConnectorValue));
        when(recordItem.getClprConnectorsRemoved()).thenReturn(List.of(bannedConnectorKey));

        // when
        clprStateChangeHandler.process(recordItem);

        // then
        var connectorCaptor = ArgumentCaptor.forClass(ClprConnector.class);
        verify(entityListener, times(2)).onClprConnector(connectorCaptor.capture());
        assertThat(connectorCaptor.getAllValues())
                .hasSize(2)
                .anySatisfy(connector -> assertThat(connector)
                        .returns(DomainUtils.toBytes(channelId), ClprConnector::getChannelId)
                        .returns(DomainUtils.toBytes(patchedConnectorId), ClprConnector::getConnectorId)
                        .returns(EntityId.of(connectorContract), ClprConnector::getConnectorContractId)
                        .returns(adminKey.toByteArray(), ClprConnector::getAdminKey)
                        .returns(90_000_000L, ClprConnector::getLockedStake)
                        .returns(1L, ClprConnector::getSlashCount)
                        .returns(1L, ClprConnector::getInFlightMessageCount)
                        .returns(null, ClprConnector::getTimestampRange)
                        .returns(null, ClprConnector::getDeleted))
                .anySatisfy(connector -> assertThat(connector)
                        .returns(DomainUtils.toBytes(channelId), ClprConnector::getChannelId)
                        .returns(DomainUtils.toBytes(bannedConnectorId), ClprConnector::getConnectorId)
                        .returns(true, ClprConnector::getDeleted)
                        .returns(Range.atLeast(consensusTimestamp), ClprConnector::getTimestampRange));
    }

    private void mockEmptyClprState() {
        lenient().when(recordItem.isSuccessful()).thenReturn(true);
        lenient().when(recordItem.getClprMessages()).thenReturn(List.of());
        lenient().when(recordItem.getClprConnectors()).thenReturn(List.of());
        lenient().when(recordItem.getClprConnectorsRemoved()).thenReturn(List.of());
    }
}
