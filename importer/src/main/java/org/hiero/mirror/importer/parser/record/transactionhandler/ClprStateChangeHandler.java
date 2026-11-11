// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.AbstractClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprConfigUpdate;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprControlConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprControlMessage;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprConnectorEntry;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprMessageEntry;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Patches whatever CLPR Channel/Message/Connector state changes are present on a {@link RecordItem}. Connector
 * patches are non-historical; channel patches historize per {@link #channelHistorizes}. Called generically from
 * {@code EntityRecordItemListener} for every transaction; no-op when none of these fields are present.
 */
@NullMarked
@Named
@RequiredArgsConstructor
public class ClprStateChangeHandler {

    /**
     * Excluded from process() since their own handler already writes onClprChannel/onClprConnector directly.
     * CLPRMAINTENANCE isn't listed -- it only carries the endpoint-manifest singleton, so process() no-ops on it.
     */
    private static final Set<TransactionType> SELF_PROCESSED_TYPES = EnumSet.of(
            TransactionType.CLPRCLOSECHANNEL,
            TransactionType.CLPRCOMPLETECHANNEL,
            TransactionType.CLPRCOMPLETECONNECTOR,
            TransactionType.CLPRDEREGISTERCONNECTOR,
            TransactionType.CLPRREGISTERCONNECTOR);

    private final ClprConnectorService clprConnectorService;
    private final EntityListener entityListener;

    public void process(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        final var clprContext = recordItem.getClprContext();
        if (clprContext == null) {
            return;
        }

        final var transactionType = TransactionType.of(recordItem.getTransactionType());
        if (SELF_PROCESSED_TYPES.contains(transactionType)) {
            return;
        }

        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        for (final var entry : clprContext.getClprChannels()) {
            final var builder = ClprChannel.builder();
            populateFromStateChange(builder, entry.channelId(), entry.value());
            if (channelHistorizes(entry.value())) {
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

        // A connector can be banned and patched in the same transaction; index patches by key so the removal below
        // can carry fields forward instead of losing them.
        final var connectorsByKey = new HashMap<ClprConnectorKey, com.hederahashgraph.api.proto.java.ClprConnector>();
        for (final var entry : clprContext.getClprConnectors()) {
            connectorsByKey.put(entry.key(), entry.value());
        }

        final var removedConnectorKeys = new HashSet<ClprConnectorKey>();
        for (final var key : clprContext.getClprConnectorsRemoved()) {
            removedConnectorKeys.add(key);
            final var removedConnector =
                    toRemovedConnector(key, connectorsByKey.get(key), transactionType, consensusTimestamp);
            if (removedConnector != null) {
                entityListener.onClprConnector(removedConnector);
            }
        }

        for (final var entry : clprContext.getClprConnectors()) {
            if (removedConnectorKeys.contains(entry.key())) {
                continue;
            }

            final var connector = toConnector(entry, transactionType, consensusTimestamp);
            if (connector == null) {
                continue;
            }

            entityListener.onClprConnector(connector);
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
            ByteString channelId,
            com.hederahashgraph.api.proto.java.ClprChannel value) {
        builder.channelId(DomainUtils.toBytes(channelId))
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
            builder.peerThrottle(ClprThrottleConverter.from(value.getPeerThrottles()));
        }

        if (value.hasEndpointManifest()) {
            builder.endpointManifest(toEndpoints(value.getEndpointManifest().getEndpointsList()));
        }
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

    private static void applyConnectorFields(
            ClprConnector.ClprConnectorBuilder<?, ?> builder, com.hederahashgraph.api.proto.java.ClprConnector value) {
        builder.connectorContractId(EntityId.of(value.getConnectorContract()))
                .adminKey(value.getAdminKey().toByteArray())
                .lockedStake(value.getLockedStake())
                .slashCount(Integer.toUnsignedLong(value.getSlashCount()))
                .inFlightMessageCount(value.getInFlightMessageCount());
    }

    // Placeholder: always historize until hiero-ledger/hiero-consensus-node#27718 lands upstream.
    private static boolean channelHistorizes(com.hederahashgraph.api.proto.java.ClprChannel value) {
        return true;
    }

    private byte @Nullable [] lookupCommitment(
            byte[] channelId, byte[] connectorId, TransactionType transactionType, long consensusTimestamp) {
        final var commitment = clprConnectorService.lookup(channelId, connectorId);
        if (commitment == null) {
            Utility.handleRecoverableError(
                    "No clpr_connector commitment found via lookup for successful {} at {}",
                    transactionType,
                    consensusTimestamp);
        }

        return commitment;
    }

    private static @Nullable ClprChannelStatus toChannelStatus(
            com.hederahashgraph.api.proto.java.ClprChannelStatus status) {
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

    private @Nullable ClprConnector toConnector(
            ClprConnectorEntry entry, TransactionType transactionType, long consensusTimestamp) {
        final var key = entry.key();
        final var channelId = DomainUtils.toBytes(key.getChannelId());
        final var connectorId = DomainUtils.toBytes(key.getConnectorId());
        final var commitment = lookupCommitment(channelId, connectorId, transactionType, consensusTimestamp);
        if (commitment == null) {
            return null;
        }

        final var builder = ClprConnector.builder()
                .channelId(channelId)
                .commitment(commitment)
                .connectorId(connectorId);
        applyConnectorFields(builder, entry.value());
        return builder.build();
    }

    private ClprControlConfiguration toControlConfiguration(
            com.hederahashgraph.api.proto.java.ClprLedgerConfiguration configuration) {
        return ClprControlConfiguration.builder()
                .protocolVersion(configuration.getProtocolVersion())
                .chainId(configuration.getChainId())
                .serviceAddress(DomainUtils.toBytes(configuration.getServiceAddress()))
                .initialTrustAnchor(DomainUtils.toBytes(configuration.getInitialTrustAnchor()))
                .initialTrustAnchorId(DomainUtils.toBytes(configuration.getInitialTrustAnchorId()))
                .throttle(ClprThrottleConverter.from(configuration))
                .build();
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

    private @Nullable ClprConnector toRemovedConnector(
            ClprConnectorKey key,
            com.hederahashgraph.api.proto.java.@Nullable ClprConnector patchedValue,
            TransactionType transactionType,
            long consensusTimestamp) {
        final var channelId = DomainUtils.toBytes(key.getChannelId());
        final var connectorId = DomainUtils.toBytes(key.getConnectorId());
        final var commitment = lookupCommitment(channelId, connectorId, transactionType, consensusTimestamp);
        if (commitment == null) {
            return null;
        }

        final var builder = ClprConnector.builder()
                .channelId(channelId)
                .commitment(commitment)
                .connectorId(connectorId)
                .status(ClprConnectorStatus.DELETED)
                .timestampRange(Range.atLeast(consensusTimestamp));

        // Carry the same-transaction patch's fields forward, otherwise mergeClprConnector backfills stale values.
        if (patchedValue != null) {
            applyConnectorFields(builder, patchedValue);
        }

        return builder.build();
    }
}
