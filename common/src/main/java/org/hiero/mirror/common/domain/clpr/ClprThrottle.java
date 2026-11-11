// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The throttle values of a {@code ClprThrottles} message, embedded in domain objects that carry them. */
@AllArgsConstructor
@Builder
@Data
@Embeddable
@NoArgsConstructor
public class ClprThrottle {
    private Long maxGasPerMessage;
    private Long maxLocalEndpoints;
    private Long maxMessagePayloadBytes;
    private Long maxMessagesPerBundle;
    private Long maxPeerEndpoints;
    private Long maxQueueDepth;
    private Long maxSyncBytes;
}
