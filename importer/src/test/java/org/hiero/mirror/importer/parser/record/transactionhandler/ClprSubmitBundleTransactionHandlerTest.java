// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprConfigUpdate;
import com.hederahashgraph.api.proto.java.ClprControlMessage;
import com.hederahashgraph.api.proto.java.ClprLedgerConfiguration;
import com.hederahashgraph.api.proto.java.ClprMessageKey;
import com.hederahashgraph.api.proto.java.ClprMessagePayload;
import com.hederahashgraph.api.proto.java.ClprMessageReply;
import com.hederahashgraph.api.proto.java.ClprMessageReplyStatus;
import com.hederahashgraph.api.proto.java.ClprMessageValue;
import com.hederahashgraph.api.proto.java.ClprSubmitBundleTransactionBody;
import com.hederahashgraph.api.proto.java.ClprThrottles;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprControlConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprMessageEntry;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.util.Version;

class ClprSubmitBundleTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ClprSubmitBundleTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setClprSubmitBundle(ClprSubmitBundleTransactionBody.newBuilder()
                        .setChannelId(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                        .setBundlePayload(DomainUtils.fromBytes(domainBuilder.bytes(64)))
                        .setEndpointNodeId(3));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        // given
        var transactionBody = getDefaultTransactionBody().build();
        var channelId = transactionBody.getClprSubmitBundle().getChannelId();
        var clprChannelValue = com.hederahashgraph.api.proto.java.ClprChannel.newBuilder()
                .setChannelId(channelId)
                .setStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE)
                .setNextMessageId(4)
                .setAckedMessageId(2)
                .setSentRunningHash(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setReceivedMessageId(1)
                .setReceivedRunningHash(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .build();

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

        // A payload with no oneof variant set -- would indicate a future, not-yet-understood protocol version;
        // the handler must log and skip it rather than persist an incomplete row.
        var unexpectedKey = ClprMessageKey.newBuilder()
                .setChannelId(channelId)
                .setMessageId(4)
                .build();
        var unexpectedValue = ClprMessageValue.newBuilder()
                .setRunningHashAfterProcessing(DomainUtils.fromBytes(domainBuilder.bytes(32)))
                .setPayload(ClprMessagePayload.newBuilder())
                .build();

        var clprMessages = List.of(
                new ClprMessageEntry(dataKey, dataValue),
                new ClprMessageEntry(replyKey, replyValue),
                new ClprMessageEntry(controlKey, controlValue),
                new ClprMessageEntry(unexpectedKey, unexpectedValue));
        var recordItem =
                buildRecordItem(transactionBody, getDefaultTransactionRecord().build(), clprChannelValue, clprMessages);
        var transaction = domainBuilder.transaction().get();
        var channelCaptor = ArgumentCaptor.forClass(ClprChannel.class);
        var messageCaptor = ArgumentCaptor.forClass(ClprMessage.class);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        verify(entityListener).onClprChannel(channelCaptor.capture());
        assertThat(channelCaptor.getValue())
                .returns(DomainUtils.toBytes(channelId), ClprChannel::getChannelId)
                .returns(4L, ClprChannel::getNextMessageId)
                .returns(2L, ClprChannel::getAckedMessageId)
                .returns(Range.atLeast(consensusTimestamp), ClprChannel::getTimestampRange);

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
                        .satisfies(m -> assertThat(m.getControlConfiguration())
                                .returns(1L, ClprControlConfiguration::getProtocolVersion)
                                .returns("hedera:mainnet", ClprControlConfiguration::getChainId)
                                .returns(100L, ClprControlConfiguration::getMaxMessagesPerBundle)));

        verify(entityListener, never()).onClprMessage(argThat(m -> m.getMessageId() == 4));
    }

    private RecordItem buildRecordItem(
            TransactionBody body,
            TransactionRecord transactionRecord,
            com.hederahashgraph.api.proto.java.ClprChannel clprChannel,
            List<ClprMessageEntry> clprMessages) {
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(body.toByteString())
                        .setSigMap(getDefaultSigMap())
                        .build()
                        .toByteString())
                .build();
        return RecordItem.builder()
                .entityTransactionPredicate(entityProperties.getPersist()::shouldPersistEntityTransaction)
                .hapiVersion(new Version(0, 28, 0))
                .transaction(transaction)
                .transactionRecord(transactionRecord)
                .clprChannel(clprChannel)
                .clprMessages(clprMessages)
                .build();
    }
}
