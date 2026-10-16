// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprControlConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprMessageEntry;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.Nullable;

@Named
@RequiredArgsConstructor
class ClprSubmitBundleTransactionHandler extends AbstractClprChannelTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRSUBMITBUNDLE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var clprChannelValue = recordItem.getClprChannel();
        if (clprChannelValue == null) {
            Utility.handleRecoverableError(
                    "No clpr_channel_value state change found for successful ClprSubmitBundle at {}",
                    consensusTimestamp);
            return;
        }

        var builder = ClprChannel.builder();
        populateFromStateChange(builder, clprChannelValue);
        entityListener.onClprChannel(
                builder.timestampRange(Range.atLeast(consensusTimestamp)).build());

        for (var entry : recordItem.getClprMessages()) {
            var message = toMessage(entry, consensusTimestamp);
            if (message != null) {
                entityListener.onClprMessage(message);
            }
        }
    }

    private @Nullable ClprMessage toMessage(ClprMessageEntry entry, long consensusTimestamp) {
        var key = entry.key();
        var value = entry.value();
        var payload = value.getPayload();
        var messageBuilder = ClprMessage.builder()
                .channelId(DomainUtils.toBytes(key.getChannelId()))
                .messageId(key.getMessageId())
                .consensusTimestamp(consensusTimestamp)
                .runningHash(DomainUtils.toBytes(value.getRunningHashAfterProcessing()));

        switch (payload.getPayloadCase()) {
            case MESSAGE -> {
                var message = payload.getMessage();
                messageBuilder
                        .messageType(ClprMessageType.DATA)
                        .connectorId(DomainUtils.toBytes(message.getConnectorId()))
                        .sender(DomainUtils.toBytes(message.getSender()))
                        .targetApplication(DomainUtils.toBytes(message.getTargetApplication()))
                        .messageData(DomainUtils.toBytes(message.getMessageData()));
            }
            case MESSAGE_REPLY -> {
                // connector_id stays null: ClprMessageReply has no connector_id field on the wire
                var reply = payload.getMessageReply();
                messageBuilder
                        .messageType(ClprMessageType.RESPONSE)
                        .replyToMessageId(reply.getMessageId())
                        .replyStatus(ClprEnums.toReplyStatus(reply.getStatus()))
                        .messageData(DomainUtils.toBytes(reply.getMessageReplyData()));
            }
            case CONTROL -> {
                messageBuilder.messageType(ClprMessageType.CONTROL);
                var control = payload.getControl();
                if (control.hasConfigUpdate()) {
                    messageBuilder.controlConfiguration(
                            toControlConfiguration(control.getConfigUpdate().getConfiguration()));
                }
            }
            default -> {
                // PAYLOAD_NOT_SET would indicate a future, not-yet-understood protocol version
                Utility.handleRecoverableError(
                        "Unexpected CLPR message payload case {} at {}", payload.getPayloadCase(), consensusTimestamp);
                return null;
            }
        }

        return messageBuilder.build();
    }

    private ClprControlConfiguration toControlConfiguration(
            com.hederahashgraph.api.proto.java.ClprLedgerConfiguration configuration) {
        var builder = ClprControlConfiguration.builder()
                .protocolVersion(configuration.getProtocolVersion())
                .chainId(configuration.getChainId())
                .serviceAddress(DomainUtils.toBytes(configuration.getServiceAddress()))
                .initialTrustAnchor(DomainUtils.toBytes(configuration.getInitialTrustAnchor()))
                .initialTrustAnchorId(DomainUtils.toBytes(configuration.getInitialTrustAnchorId()));

        if (configuration.hasThrottles()) {
            var throttles = configuration.getThrottles();
            builder.maxGasPerMessage(throttles.getMaxGasPerMessage())
                    .maxLocalEndpoints((long) throttles.getMaxLocalEndpoints())
                    .maxMessagePayloadBytes((long) throttles.getMaxMessagePayloadBytes())
                    .maxMessagesPerBundle((long) throttles.getMaxMessagesPerBundle())
                    .maxPeerEndpoints((long) throttles.getMaxPeerEndpoints())
                    .maxQueueDepth((long) throttles.getMaxQueueDepth())
                    .maxSyncBytes(throttles.getMaxSyncBytes());
        }

        return builder.build();
    }
}
