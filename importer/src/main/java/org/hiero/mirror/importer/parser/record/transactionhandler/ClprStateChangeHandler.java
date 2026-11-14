// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.hiero.mirror.common.domain.transaction.StateChangeContext.isPresent;

import com.google.common.collect.Range;
import com.hedera.hapi.block.stream.output.protoc.SingletonUpdateChange;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprConfigUpdate;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprControlConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprControlMessage;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprChannelEntry;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprConnectorEntry;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprMessageEntry;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Patches whatever CLPR Channel/Message/Connector/singleton state changes are present on a {@link RecordItem}.
 * Channel patches historize per {@link #channelHistorizes}; connector patches historize per
 * {@link #connectorHistorizes}. A partial singleton (endpoint manifest/ledger configuration) update is merged
 * against the last known full value via {@link #clprEndpointManifestService}/{@link #clprLedgerConfigurationService}.
 * Called generically from {@code EntityRecordItemListener} for every transaction; no-op when none of these fields
 * are present.
 */
@NullMarked
@Named
@RequiredArgsConstructor
public class ClprStateChangeHandler {

    /**
     * Excluded from process() since their own handler already writes onClprChannel/onClprConnector directly.
     * CLPRMAINTENANCE and CLPRUPDATELEDGERCONFIGURATION aren't listed -- they only carry the singleton state
     * changes, which process() always handles generically.
     */
    private static final Set<TransactionType> SELF_PROCESSED_TYPES = EnumSet.of(
            TransactionType.CLPRCLOSECHANNEL,
            TransactionType.CLPRCOMPLETECHANNEL,
            TransactionType.CLPRCOMPLETECONNECTOR,
            TransactionType.CLPRDEREGISTERCONNECTOR,
            TransactionType.CLPRREGISTERCONNECTOR);

    private final ClprConnectorService clprConnectorService;
    private final ClprEndpointManifestService clprEndpointManifestService;
    private final ClprLedgerConfigurationService clprLedgerConfigurationService;
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
            final var builder = populateFromStateChange(entry);
            if (channelHistorizes(entry)) {
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

        for (final var key : clprContext.getClprConnectorsRemoved()) {
            final var removedConnector = toRemovedConnector(key, transactionType, consensusTimestamp);
            if (removedConnector != null) {
                entityListener.onClprConnector(removedConnector);
            }
        }

        for (final var entry : clprContext.getClprConnectors()) {
            final var connector = toConnector(entry, transactionType, consensusTimestamp);
            if (connector == null) {
                continue;
            }

            entityListener.onClprConnector(connector);
        }

        final var endpointManifestUpdate = clprContext.getClprEndpointManifestUpdate();
        if (endpointManifestUpdate != null) {
            final var manifest = toEndpointManifest(endpointManifestUpdate, consensusTimestamp);
            if (manifest != null) {
                entityListener.onClprEndpointManifest(manifest);
                clprEndpointManifestService.notify(manifest);
            }
        }

        final var ledgerConfigurationUpdate = clprContext.getClprLedgerConfigurationUpdate();
        if (ledgerConfigurationUpdate != null) {
            final var configuration = toLedgerConfiguration(ledgerConfigurationUpdate, consensusTimestamp);
            if (configuration != null) {
                entityListener.onClprLedgerConfiguration(configuration);
                clprLedgerConfigurationService.notify(configuration);
            }
        }
    }

    static void applyConnectorFields(ClprConnector.ClprConnectorBuilder<?, ?> builder, ClprConnectorEntry entry) {
        final var mapUpdate = entry.mapUpdate();
        final var value = entry.value();

        if (isPresent(
                mapUpdate, value, com.hederahashgraph.api.proto.java.ClprConnector.CONNECTOR_CONTRACT_FIELD_NUMBER)) {
            builder.connectorContractId(EntityId.of(value.getConnectorContract()));
        }
        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprConnector.ADMIN_KEY_FIELD_NUMBER)) {
            builder.adminKey(value.getAdminKey().toByteArray());
        }
        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprConnector.LOCKED_STAKE_FIELD_NUMBER)) {
            builder.lockedStake(value.getLockedStake());
        }
        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprConnector.SLASH_COUNT_FIELD_NUMBER)) {
            builder.slashCount(Integer.toUnsignedLong(value.getSlashCount()));
        }
        if (isPresent(
                mapUpdate,
                value,
                com.hederahashgraph.api.proto.java.ClprConnector.IN_FLIGHT_MESSAGE_COUNT_FIELD_NUMBER)) {
            builder.inFlightMessageCount(value.getInFlightMessageCount());
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

    /**
     * Populates every field present in {@code entry}'s state change. A full update has every field present; a
     * partial update only has the top-level fields that changed or were explicitly cleared -- any field absent from
     * a partial update is left unset on the builder so the upsert/merge layer carries the existing value forward.
     */
    static ClprChannel.ClprChannelBuilder<?, ?> populateFromStateChange(ClprChannelEntry entry) {
        final var mapUpdate = entry.mapUpdate();
        final var value = entry.value();
        final var builder = ClprChannel.builder().channelId(DomainUtils.toBytes(entry.channelId()));

        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.STATUS_FIELD_NUMBER)) {
            builder.status(toChannelStatus(value.getStatus()));
        }
        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.NEXT_MESSAGE_ID_FIELD_NUMBER)) {
            builder.nextMessageId(value.getNextMessageId());
        }
        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.ACKED_MESSAGE_ID_FIELD_NUMBER)) {
            builder.ackedMessageId(value.getAckedMessageId());
        }
        if (isPresent(
                mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.SENT_RUNNING_HASH_FIELD_NUMBER)) {
            builder.sentRunningHash(DomainUtils.toBytes(value.getSentRunningHash()));
        }
        if (isPresent(
                mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.RECEIVED_MESSAGE_ID_FIELD_NUMBER)) {
            builder.receivedMessageId(value.getReceivedMessageId());
        }
        if (isPresent(
                mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.RECEIVED_RUNNING_HASH_FIELD_NUMBER)) {
            builder.receivedRunningHash(DomainUtils.toBytes(value.getReceivedRunningHash()));
        }
        if (isPresent(
                mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.OWNERSHIP_COMMITMENT_FIELD_NUMBER)) {
            builder.ownershipCommitment(DomainUtils.toBytes(value.getOwnershipCommitment()));
        }
        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.TRUST_ANCHOR_FIELD_NUMBER)) {
            builder.trustAnchor(DomainUtils.toBytes(value.getTrustAnchor()));
        }
        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.TRUST_ANCHOR_ID_FIELD_NUMBER)) {
            builder.trustAnchorId(DomainUtils.toBytes(value.getTrustAnchorId()));
        }
        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.CHANNEL_CONTEXT_FIELD_NUMBER)) {
            builder.channelContext(DomainUtils.toBytes(value.getChannelContext()));
        }
        if (isPresent(
                mapUpdate,
                value,
                com.hederahashgraph.api.proto.java.ClprChannel.ENDPOINT_MANIFEST_VERSION_FIELD_NUMBER)) {
            builder.endpointManifestVersion(value.getEndpointManifestVersion());
        }
        if (isPresent(
                mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.LAST_CONFIG_TIMESTAMP_FIELD_NUMBER)) {
            builder.lastConfigTimestamp(
                    value.hasLastConfigTimestamp()
                            ? DomainUtils.timestampInNanosMax(value.getLastConfigTimestamp())
                            : null);
        }
        if (isPresent(
                mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.PEER_CONFIG_TIMESTAMP_FIELD_NUMBER)) {
            builder.peerConfigTimestamp(
                    value.hasPeerConfigTimestamp()
                            ? DomainUtils.timestampInNanosMax(value.getPeerConfigTimestamp())
                            : null);
        }
        if (isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.PEER_THROTTLES_FIELD_NUMBER)) {
            builder.peerThrottle(
                    value.hasPeerThrottles() ? ClprThrottleConverter.from(value.getPeerThrottles()) : null);
        }
        if (isPresent(
                mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.ENDPOINT_MANIFEST_FIELD_NUMBER)) {
            builder.endpointManifest(
                    value.hasEndpointManifest()
                            ? toEndpoints(value.getEndpointManifest().getEndpointsList())
                            : List.of());
        }

        return builder;
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

    // A full update (partial=false) can't tell us which field actually changed, so conservatively always historize
    // -- same as before hiero-ledger/hiero-consensus-node#27718. A partial update lists exactly the fields that
    // changed or were cleared, so historize iff one of them is outside the counter-type fields (next_message_id,
    // acked_message_id, sent_running_hash, received_message_id, received_running_hash) that churn on every message.
    private static boolean channelHistorizes(ClprChannelEntry entry) {
        final var mapUpdate = entry.mapUpdate();
        if (!mapUpdate.getPartial()) {
            return true;
        }

        final var value = entry.value();
        return isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.STATUS_FIELD_NUMBER)
                || isPresent(
                        mapUpdate,
                        value,
                        com.hederahashgraph.api.proto.java.ClprChannel.OWNERSHIP_COMMITMENT_FIELD_NUMBER)
                || isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.TRUST_ANCHOR_FIELD_NUMBER)
                || isPresent(
                        mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.TRUST_ANCHOR_ID_FIELD_NUMBER)
                || isPresent(
                        mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.CHANNEL_CONTEXT_FIELD_NUMBER)
                || isPresent(
                        mapUpdate,
                        value,
                        com.hederahashgraph.api.proto.java.ClprChannel.ENDPOINT_MANIFEST_VERSION_FIELD_NUMBER)
                || isPresent(
                        mapUpdate,
                        value,
                        com.hederahashgraph.api.proto.java.ClprChannel.LAST_CONFIG_TIMESTAMP_FIELD_NUMBER)
                || isPresent(
                        mapUpdate,
                        value,
                        com.hederahashgraph.api.proto.java.ClprChannel.PEER_CONFIG_TIMESTAMP_FIELD_NUMBER)
                || isPresent(
                        mapUpdate, value, com.hederahashgraph.api.proto.java.ClprChannel.PEER_THROTTLES_FIELD_NUMBER)
                || isPresent(
                        mapUpdate,
                        value,
                        com.hederahashgraph.api.proto.java.ClprChannel.ENDPOINT_MANIFEST_FIELD_NUMBER);
    }

    // A full update (partial=false) can't tell us which field actually changed, so conservatively always historize,
    // same as channelHistorizes. A partial update lists exactly the fields that changed or were cleared, so
    // historize iff one of them is outside the counter-type fields (in_flight_message_count, locked_stake,
    // slash_count) that churn on every message/slash.
    private static boolean connectorHistorizes(ClprConnectorEntry entry) {
        final var mapUpdate = entry.mapUpdate();
        if (!mapUpdate.getPartial()) {
            return true;
        }

        final var value = entry.value();
        return isPresent(
                        mapUpdate,
                        value,
                        com.hederahashgraph.api.proto.java.ClprConnector.CONNECTOR_CONTRACT_FIELD_NUMBER)
                || isPresent(mapUpdate, value, com.hederahashgraph.api.proto.java.ClprConnector.ADMIN_KEY_FIELD_NUMBER);
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

        if (status == com.hederahashgraph.api.proto.java.ClprChannelStatus.PENDING) {
            // PENDING is pre-creation only, tracked via ClprChannelPendingCommitment;
            // an existing ClprChannel state-change value should never revert to it.
            Utility.handleRecoverableError("ClprChannel state change unexpectedly reverted to PENDING status");
            return null;
        }

        try {
            return ClprChannelStatus.valueOf(status.name());
        } catch (IllegalArgumentException e) {
            Utility.handleRecoverableError("Unexpected CLPR channel status {}", status);
            return null;
        }
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
        applyConnectorFields(builder, entry);
        if (connectorHistorizes(entry)) {
            builder.timestampRange(Range.atLeast(consensusTimestamp));
        }
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

    private @Nullable ClprControlMessage toControlMessage(
            com.hederahashgraph.api.proto.java.ClprControlMessage control, long consensusTimestamp) {
        return switch (control.getPayloadCase()) {
            case CONFIG_UPDATE ->
                ClprControlMessage.builder()
                        .configUpdate(ClprConfigUpdate.builder()
                                .configuration(toControlConfiguration(
                                        control.getConfigUpdate().getConfiguration()))
                                .build())
                        .build();
            default -> {
                // PAYLOAD_NOT_SET would indicate a future, not-yet-understood protocol version
                Utility.handleRecoverableError(
                        "Unexpected CLPR control message payload case {} at {}",
                        control.getPayloadCase(),
                        consensusTimestamp);
                yield null;
            }
        };
    }

    /**
     * Builds the full {@link ClprEndpointManifest} row for a singleton state change. A full update replaces every
     * field; a partial update only carries the fields that changed -- any other field is merged in from the last
     * known full value, since this is an append-only, no-history domain object and every write must be complete.
     */
    private @Nullable ClprEndpointManifest toEndpointManifest(SingletonUpdateChange update, long consensusTimestamp) {
        final var value = update.getClprEndpointManifestValue();
        if (!update.getPartial()) {
            return ClprEndpointManifest.builder()
                    .consensusTimestamp(consensusTimestamp)
                    .endpoints(toEndpoints(value.getEndpointsList()))
                    .serviceAddress(DomainUtils.toBytes(value.getServiceAddress()))
                    .version(value.getVersion())
                    .build();
        }

        final var baseline = clprEndpointManifestService.lookup();
        if (baseline == null) {
            Utility.handleRecoverableError(
                    "No clpr_endpoint_manifest baseline found for partial singleton update at {}", consensusTimestamp);
            return null;
        }

        return ClprEndpointManifest.builder()
                .consensusTimestamp(consensusTimestamp)
                .endpoints(
                        isPresent(
                                        update,
                                        value,
                                        com.hederahashgraph.api.proto.java.ClprEndpointManifest.ENDPOINTS_FIELD_NUMBER)
                                ? toEndpoints(value.getEndpointsList())
                                : baseline.getEndpoints())
                .serviceAddress(
                        isPresent(
                                        update,
                                        value,
                                        com.hederahashgraph.api.proto.java.ClprEndpointManifest
                                                .SERVICE_ADDRESS_FIELD_NUMBER)
                                ? DomainUtils.toBytes(value.getServiceAddress())
                                : baseline.getServiceAddress())
                .version(
                        isPresent(
                                        update,
                                        value,
                                        com.hederahashgraph.api.proto.java.ClprEndpointManifest.VERSION_FIELD_NUMBER)
                                ? value.getVersion()
                                : baseline.getVersion())
                .build();
    }

    /**
     * Builds the full {@link ClprLedgerConfiguration} row for a singleton state change. Same merge rule as
     * {@link #toEndpointManifest}.
     */
    private @Nullable ClprLedgerConfiguration toLedgerConfiguration(
            SingletonUpdateChange update, long consensusTimestamp) {
        final var value = update.getClprLedgerConfigurationValue();
        if (!update.getPartial()) {
            return ClprLedgerConfiguration.builder()
                    .chainId(value.getChainId())
                    .consensusTimestamp(consensusTimestamp)
                    .initialTrustAnchor(DomainUtils.toBytes(value.getInitialTrustAnchor()))
                    .initialTrustAnchorId(DomainUtils.toBytes(value.getInitialTrustAnchorId()))
                    .protocolVersion(Integer.toUnsignedLong(value.getProtocolVersion()))
                    .serviceAddress(DomainUtils.toBytes(value.getServiceAddress()))
                    .throttle(ClprThrottleConverter.from(value))
                    .build();
        }

        final var baseline = clprLedgerConfigurationService.lookup();
        if (baseline == null) {
            Utility.handleRecoverableError(
                    "No clpr_ledger_configuration baseline found for partial singleton update at {}",
                    consensusTimestamp);
            return null;
        }

        return ClprLedgerConfiguration.builder()
                .chainId(
                        isPresent(
                                        update,
                                        value,
                                        com.hederahashgraph.api.proto.java.ClprLedgerConfiguration
                                                .CHAIN_ID_FIELD_NUMBER)
                                ? value.getChainId()
                                : baseline.getChainId())
                .consensusTimestamp(consensusTimestamp)
                .initialTrustAnchor(
                        isPresent(
                                        update,
                                        value,
                                        com.hederahashgraph.api.proto.java.ClprLedgerConfiguration
                                                .INITIAL_TRUST_ANCHOR_FIELD_NUMBER)
                                ? DomainUtils.toBytes(value.getInitialTrustAnchor())
                                : baseline.getInitialTrustAnchor())
                .initialTrustAnchorId(
                        isPresent(
                                        update,
                                        value,
                                        com.hederahashgraph.api.proto.java.ClprLedgerConfiguration
                                                .INITIAL_TRUST_ANCHOR_ID_FIELD_NUMBER)
                                ? DomainUtils.toBytes(value.getInitialTrustAnchorId())
                                : baseline.getInitialTrustAnchorId())
                .protocolVersion(
                        isPresent(
                                        update,
                                        value,
                                        com.hederahashgraph.api.proto.java.ClprLedgerConfiguration
                                                .PROTOCOL_VERSION_FIELD_NUMBER)
                                ? Integer.toUnsignedLong(value.getProtocolVersion())
                                : baseline.getProtocolVersion())
                .serviceAddress(
                        isPresent(
                                        update,
                                        value,
                                        com.hederahashgraph.api.proto.java.ClprLedgerConfiguration
                                                .SERVICE_ADDRESS_FIELD_NUMBER)
                                ? DomainUtils.toBytes(value.getServiceAddress())
                                : baseline.getServiceAddress())
                .throttle(
                        isPresent(
                                        update,
                                        value,
                                        com.hederahashgraph.api.proto.java.ClprLedgerConfiguration
                                                .THROTTLES_FIELD_NUMBER)
                                ? ClprThrottleConverter.from(value)
                                : baseline.getThrottle())
                .build();
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
                final var controlMessage = toControlMessage(payload.getControl(), consensusTimestamp);
                if (controlMessage == null) {
                    return null;
                }
                messageBuilder.messageType(ClprMessageType.CONTROL).controlMessage(controlMessage);
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
            ClprConnectorKey key, TransactionType transactionType, long consensusTimestamp) {
        final var channelId = DomainUtils.toBytes(key.getChannelId());
        final var connectorId = DomainUtils.toBytes(key.getConnectorId());
        final var commitment = lookupCommitment(channelId, connectorId, transactionType, consensusTimestamp);
        if (commitment == null) {
            return null;
        }

        return ClprConnector.builder()
                .channelId(channelId)
                .commitment(commitment)
                .connectorId(connectorId)
                .status(ClprConnectorStatus.DELETED)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
    }
}
