// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.clpr.AbstractClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.util.Utility;

/**
 * Shared mapping of the verifier-derived {@code clpr_channel_value} state change onto a {@link AbstractClprChannel}
 * builder, used by every CLPR transaction handler that reads {@link RecordItem#getClprChannel()}
 * ({@code ClprCompleteChannel}, {@code ClprSubmitBundle}, {@code ClprCloseChannel}) -- see
 * {@link org.hiero.mirror.common.domain.transaction.RecordItem} for why this data can't be read off the
 * transaction body or classic {@code TransactionRecord}.
 */
abstract class AbstractClprChannelTransactionHandler extends AbstractTransactionHandler {

    protected void populateFromStateChange(
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

    private List<ClprEndpoint> toEndpoints(List<com.hederahashgraph.api.proto.java.ClprEndpoint> endpoints) {
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

    private ClprChannelStatus toChannelStatus(com.hederahashgraph.api.proto.java.ClprChannelStatus status) {
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
}
