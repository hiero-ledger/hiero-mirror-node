// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.AbstractClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprConfigUpdate;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprControlConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprControlMessage;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprMessageEntry;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.Nullable;

/**
 * Patches whatever CLPR Channel/Message/Connector state changes are present on a {@link RecordItem}. Connector
 * patches are non-historical; channel patches historize per {@link #channelHistorizes}. Shared by
 * {@code ClprSubmitBundleTransactionHandler}, {@code ContractCallTransactionHandler}, and
 * {@code EthereumTransactionHandler} (the CLPR {@code sendMessage} system contract can run inside the latter two).
 * No-op when none of these fields are present.
 */
@Named
@RequiredArgsConstructor
class ClprStateChangeHandler {

    private final EntityListener entityListener;

    static void populateFromStateChange(
            AbstractClprChannel.AbstractClprChannelBuilder<?, ?> builder,
            com.hederahashgraph.api.proto.java.ClprChannel value) {
        builder.channelId(DomainUtils.toBytes(value.getChannelId()))
                .status(toChannelStatus(value.getStatus()))
                .nextMessageId(value.getNextMessageId())
                .ackedMessageId(value.getAckedMessageId())
                .sentRunningHash(DomainUtils.toBytes(value.getSentRunningHash()))
                .receivedMessageId(value.getReceivedMessageId())
                .receivedRunningHash(DomainUtils.toBytes(value.getReceivedRunningHash()))
                .ownershipCommitment(DomainUtils.toBytes(value.getOwnershipCommitment()))
                .trustAnchor(DomainUtils.toBytes(value.getTrustAnchor()))
                .trustAnchorId(DomainUtils.toBytes(value.getTrustAnchorId()))
                .channelContext(DomainUtils.toBytes(value.getChannelContext()))
                .endpointManifestVersion(value.getEndpointManifestVersion());

        if (value.hasLastConfigTimestamp()) {
            builder.lastConfigTimestamp(DomainUtils.timestampInNanosMax(value.getLastConfigTimestamp()));
        }

        if (value.hasPeerConfigTimestamp()) {
            builder.peerConfigTimestamp(DomainUtils.timestampInNanosMax(value.getPeerConfigTimestamp()));
        }

        if (value.hasPeerThrottles()) {
            var throttles = value.getPeerThrottles();
            builder.peerMaxGasPerMessage(throttles.getMaxGasPerMessage())
                    .peerMaxLocalEndpoints((long) throttles.getMaxLocalEndpoints())
                    .peerMaxMessagePayloadBytes((long) throttles.getMaxMessagePayloadBytes())
                    .peerMaxMessagesPerBundle((long) throttles.getMaxMessagesPerBundle())
                    .peerMaxPeerEndpoints((long) throttles.getMaxPeerEndpoints())
                    .peerMaxQueueDepth((long) throttles.getMaxQueueDepth())
                    .peerMaxSyncBytes(throttles.getMaxSyncBytes());
        }

        if (value.hasEndpointManifest()) {
            builder.endpointManifest(toEndpoints(value.getEndpointManifest().getEndpointsList()));
        }
    }

    // Placeholder: always historize until hiero-ledger/hiero-consensus-node#27718 lands upstream.
    private static boolean channelHistorizes(com.hederahashgraph.api.proto.java.ClprChannel value) {
        return true;
    }

    private static ClprChannelStatus toChannelStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus status) {
        return switch (status) {
            case ACTIVE -> ClprChannelStatus.ACTIVE;
            case PAUSED -> ClprChannelStatus.PAUSED;
            case CLOSING -> ClprChannelStatus.CLOSING;
            case DRAINED -> ClprChannelStatus.DRAINED;
            case CLOSED -> ClprChannelStatus.CLOSED;
            default -> {
                Utility.handleRecoverableError("Unexpected CLPR channel status {}", status);
                yield null;
            }
        };
    }

    static List<ClprEndpoint> toEndpoints(List<com.hederahashgraph.api.proto.java.ClprEndpoint> endpoints) {
        var result = new ArrayList<ClprEndpoint>(endpoints.size());
        for (var endpoint : endpoints) {
            var serviceEndpoint = endpoint.getServiceEndpoint();
            result.add(ClprEndpoint.builder()
                    .serviceEndpoint(new ClprServiceEndpoint(serviceEndpoint.getIpAddress(), serviceEndpoint.getPort()))
                    .tlsCertificate(DomainUtils.toBytes(endpoint.getTlsCertificate()))
                    .accountId(DomainUtils.toBytes(endpoint.getAccountId()))
                    .build());
        }
        return result;
    }

    void process(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var clprChannelValue = recordItem.getClprChannel();
        if (clprChannelValue != null) {
            var builder = ClprChannel.builder();
            populateFromStateChange(builder, clprChannelValue);
            if (channelHistorizes(clprChannelValue)) {
                builder.timestampRange(Range.atLeast(consensusTimestamp));
            }
            entityListener.onClprChannel(builder.build());
        }

        for (var entry : recordItem.getClprMessages()) {
            var message = toMessage(entry, consensusTimestamp);
            if (message != null) {
                entityListener.onClprMessage(message);
            }
        }

        // Process removals first so a connector banned and patched in the same transaction ends up deleted.
        var removedConnectorKeys = new HashSet<ClprConnectorKey>();
        for (var key : recordItem.getClprConnectorsRemoved()) {
            removedConnectorKeys.add(key);
            entityListener.onClprConnector(toRemovedConnector(key, consensusTimestamp));
        }

        for (var value : recordItem.getClprConnectors()) {
            var key = ClprConnectorKey.newBuilder()
                    .setChannelId(value.getChannelId())
                    .setConnectorId(value.getConnectorId())
                    .build();
            if (!removedConnectorKeys.contains(key)) {
                entityListener.onClprConnector(toConnector(value));
            }
        }
    }

    private ClprConnector toConnector(com.hederahashgraph.api.proto.java.ClprConnector value) {
        return ClprConnector.builder()
                .channelId(DomainUtils.toBytes(value.getChannelId()))
                .connectorId(DomainUtils.toBytes(value.getConnectorId()))
                .connectorContractId(EntityId.of(value.getConnectorContract()))
                .adminKey(value.getAdminKey().toByteArray())
                .lockedStake(value.getLockedStake())
                .slashCount((long) value.getSlashCount())
                .inFlightMessageCount(value.getInFlightMessageCount())
                .build();
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

    private ClprControlMessage toControlMessage(com.hederahashgraph.api.proto.java.ClprControlMessage control) {
        var builder = ClprControlMessage.builder();
        if (control.hasConfigUpdate()) {
            builder.configUpdate(ClprConfigUpdate.builder()
                    .configuration(
                            toControlConfiguration(control.getConfigUpdate().getConfiguration()))
                    .build());
        }
        return builder.build();
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
                messageBuilder
                        .messageType(ClprMessageType.CONTROL)
                        .controlMessage(toControlMessage(payload.getControl()));
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

    private ClprConnector toRemovedConnector(ClprConnectorKey key, long consensusTimestamp) {
        return ClprConnector.builder()
                .channelId(DomainUtils.toBytes(key.getChannelId()))
                .connectorId(DomainUtils.toBytes(key.getConnectorId()))
                .deleted(true)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
    }
}
