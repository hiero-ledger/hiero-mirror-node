// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Flattens a {@code ClprThrottles} message into the individual fields used by domain objects that embed it. */
@NullMarked
record ClprThrottleValues(
        long maxGasPerMessage,
        long maxLocalEndpoints,
        long maxMessagePayloadBytes,
        long maxMessagesPerBundle,
        long maxPeerEndpoints,
        long maxQueueDepth,
        long maxSyncBytes) {

    static @Nullable ClprThrottleValues from(com.hederahashgraph.api.proto.java.ClprLedgerConfiguration configuration) {
        return configuration.hasThrottles() ? from(configuration.getThrottles()) : null;
    }

    static ClprThrottleValues from(com.hederahashgraph.api.proto.java.ClprThrottles throttles) {
        return new ClprThrottleValues(
                throttles.getMaxGasPerMessage(),
                Integer.toUnsignedLong(throttles.getMaxLocalEndpoints()),
                Integer.toUnsignedLong(throttles.getMaxMessagePayloadBytes()),
                Integer.toUnsignedLong(throttles.getMaxMessagesPerBundle()),
                Integer.toUnsignedLong(throttles.getMaxPeerEndpoints()),
                Integer.toUnsignedLong(throttles.getMaxQueueDepth()),
                throttles.getMaxSyncBytes());
    }
}
