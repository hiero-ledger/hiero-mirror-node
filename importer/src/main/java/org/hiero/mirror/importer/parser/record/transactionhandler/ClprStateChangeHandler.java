// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.Nullable;

/**
 * Patches whatever CLPR Channel/Message/Connector state changes are present on a {@link RecordItem}. Connector
 * patches are non-historical; channel patches historize per {@link #channelHistorizes}. Called generically from
 * {@code EntityRecordItemListener} for every transaction; no-op when none of these fields are present.
 */
@Named
@RequiredArgsConstructor
public class ClprStateChangeHandler {

    /** Excluded from process() since their own handler already writes onClprChannel/onClprConnector directly. */
    private static final Set<TransactionType> SELF_PROCESSED_TYPES = EnumSet.of(
            TransactionType.CLPRCLOSECHANNEL,
            TransactionType.CLPRCOMPLETECHANNEL,
            TransactionType.CLPRCOMPLETECONNECTOR,
            TransactionType.CLPRDEREGISTERCONNECTOR);

    private final EntityListener entityListener;

    public void process(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        final var clprContext = recordItem.getClprContext();
        if (clprContext == null) {
            return;
        }

        if (SELF_PROCESSED_TYPES.contains(TransactionType.of(recordItem.getTransactionType()))) {
            return;
        }

        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var clprChannelValue = clprContext.getClprChannel();
        if (clprChannelValue != null) {
            final var builder = ClprChannel.builder();
            populateFromStateChange(builder, clprChannelValue);
            if (channelHistorizes(clprChannelValue)) {
                builder.timestampRange(Range.atLeast(consensusTimestamp));
            }
            entityListener.onClprChannel(builder.build());
        }

        for (final var entry : clprContext.getClprMessages()) {
            final var message = toMessage(entry, consensusTimestamp);
            if (message != null) {
                entityListener.onClprMessage(message);
            }
        }

        // Process removals first so a connector banned and patched in the same transaction ends up deleted.
        final var removedConnectorKeys = new HashSet<ClprConnectorKey>();
        for (final var key : clprContext.getClprConnectorsRemoved()) {
            removedConnectorKeys.add(key);
            entityListener.onClprConnector(toRemovedConnector(key, consensusTimestamp));
        }

        for (final var value : clprContext.getClprConnectors()) {
            final var key = ClprConnectorKey.newBuilder()
                    .setChannelId(value.getChannelId())
                    .setConnectorId(value.getConnectorId())
                    .build();
            if (!removedConnectorKeys.contains(key)) {
                entityListener.onClprConnector(toConnector(value));
            }
        }
    }

    /**
     * Logs and reports a missing state change for a successful transaction that depends on one. {@code value} is
     * typed {@code Object} since callers pass whichever state-change shape (a protobuf message, a commitment byte
     * string, etc.) their transaction depends on -- only nullness is checked here.
     */
    static boolean isMissingStateChange(
            @Nullable Object value, String description, String transactionName, long consensusTimestamp) {
        if (value != null) {
            return false;
        }

        Utility.handleRecoverableError(
                "No {} state change found for successful {} at {}", description, transactionName, consensusTimestamp);
        return true;
    }

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
            final var throttles = value.getPeerThrottles();
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
        final var result = new ArrayList<ClprEndpoint>(endpoints.size());
        for (final var endpoint : endpoints) {
            final var serviceEndpoint = endpoint.getServiceEndpoint();
            result.add(ClprEndpoint.builder()
                    .serviceEndpoint(new ClprServiceEndpoint(serviceEndpoint.getIpAddress(), serviceEndpoint.getPort()))
                    .tlsCertificate(DomainUtils.toBytes(endpoint.getTlsCertificate()))
                    .accountId(DomainUtils.toBytes(endpoint.getAccountId()))
                    .build());
        }
        return result;
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
        final var builder = ClprControlConfiguration.builder()
                .protocolVersion(configuration.getProtocolVersion())
                .chainId(configuration.getChainId())
                .serviceAddress(DomainUtils.toBytes(configuration.getServiceAddress()))
                .initialTrustAnchor(DomainUtils.toBytes(configuration.getInitialTrustAnchor()))
                .initialTrustAnchorId(DomainUtils.toBytes(configuration.getInitialTrustAnchorId()));

        final var throttleValues = ClprThrottleValues.from(configuration);
        if (throttleValues != null) {
            builder.maxGasPerMessage(throttleValues.maxGasPerMessage())
                    .maxLocalEndpoints(throttleValues.maxLocalEndpoints())
                    .maxMessagePayloadBytes(throttleValues.maxMessagePayloadBytes())
                    .maxMessagesPerBundle(throttleValues.maxMessagesPerBundle())
                    .maxPeerEndpoints(throttleValues.maxPeerEndpoints())
                    .maxQueueDepth(throttleValues.maxQueueDepth())
                    .maxSyncBytes(throttleValues.maxSyncBytes());
        }

        return builder.build();
    }

    private ClprControlMessage toControlMessage(com.hederahashgraph.api.proto.java.ClprControlMessage control) {
        final var builder = ClprControlMessage.builder();
        if (control.hasConfigUpdate()) {
            builder.configUpdate(ClprConfigUpdate.builder()
                    .configuration(
                            toControlConfiguration(control.getConfigUpdate().getConfiguration()))
                    .build());
        }
        return builder.build();
    }

    private @Nullable ClprMessage toMessage(ClprMessageEntry entry, long consensusTimestamp) {
        final var key = entry.key();
        final var value = entry.value();
        final var payload = value.getPayload();
        final var messageBuilder = ClprMessage.builder()
                .channelId(DomainUtils.toBytes(key.getChannelId()))
                .messageId(key.getMessageId())
                .consensusTimestamp(consensusTimestamp)
                .runningHash(DomainUtils.toBytes(value.getRunningHashAfterProcessing()));

        switch (payload.getPayloadCase()) {
            case MESSAGE -> {
                final var message = payload.getMessage();
                messageBuilder
                        .messageType(ClprMessageType.DATA)
                        .connectorId(DomainUtils.toBytes(message.getConnectorId()))
                        .sender(DomainUtils.toBytes(message.getSender()))
                        .targetApplication(DomainUtils.toBytes(message.getTargetApplication()))
                        .messageData(DomainUtils.toBytes(message.getMessageData()));
            }
            case MESSAGE_REPLY -> {
                // connector_id stays null: ClprMessageReply has no connector_id field on the wire
                final var reply = payload.getMessageReply();
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
