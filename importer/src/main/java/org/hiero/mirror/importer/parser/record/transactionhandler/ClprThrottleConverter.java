// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import lombok.experimental.UtilityClass;
import org.hiero.mirror.common.domain.clpr.ClprThrottle;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Converts a {@code ClprThrottles} message into the domain {@link ClprThrottle} embedded by the callers. */
@NullMarked
@UtilityClass
class ClprThrottleConverter {

    static @Nullable ClprThrottle from(com.hederahashgraph.api.proto.java.ClprLedgerConfiguration configuration) {
        return configuration.hasThrottles() ? from(configuration.getThrottles()) : null;
    }

    static ClprThrottle from(com.hederahashgraph.api.proto.java.ClprThrottles throttles) {
        return ClprThrottle.builder()
                .maxGasPerMessage(throttles.getMaxGasPerMessage())
                .maxLocalEndpoints(Integer.toUnsignedLong(throttles.getMaxLocalEndpoints()))
                .maxMessagePayloadBytes(Integer.toUnsignedLong(throttles.getMaxMessagePayloadBytes()))
                .maxMessagesPerBundle(Integer.toUnsignedLong(throttles.getMaxMessagesPerBundle()))
                .maxPeerEndpoints(Integer.toUnsignedLong(throttles.getMaxPeerEndpoints()))
                .maxQueueDepth(Integer.toUnsignedLong(throttles.getMaxQueueDepth()))
                .maxSyncBytes(throttles.getMaxSyncBytes())
                .build();
    }
}
