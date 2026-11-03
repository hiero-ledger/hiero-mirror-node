// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code ClprLedgerConfiguration} propagated via a Control message's {@code ClprConfigUpdate}, stored as
 * {@code jsonb} in {@code clpr_message.control_configuration}. A Lombok class rather than a record since
 * {@code byte[]} record components get identity-based, not value-based, {@code equals()}/{@code hashCode()}.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@NoArgsConstructor
public class ClprControlConfiguration {
    private long protocolVersion;
    private String chainId;
    private byte[] serviceAddress;
    private Long maxGasPerMessage;
    private Long maxLocalEndpoints;
    private Long maxMessagePayloadBytes;
    private Long maxMessagesPerBundle;
    private Long maxPeerEndpoints;
    private Long maxQueueDepth;
    private Long maxSyncBytes;
    private byte[] initialTrustAnchor;
    private byte[] initialTrustAnchorId;
}
