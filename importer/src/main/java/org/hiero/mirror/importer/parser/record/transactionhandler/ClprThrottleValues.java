// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import org.jspecify.annotations.Nullable;

/**
 * Flattens {@code ClprLedgerConfiguration.throttles}, shared by {@code ClprControlConfiguration} (Control message
 * propagation) and {@code ClprLedgerConfiguration} (ledger configuration storage), both of which model the same
 * seven throttle values as individual fields.
 */
record ClprThrottleValues(
        long maxGasPerMessage,
        long maxLocalEndpoints,
        long maxMessagePayloadBytes,
        long maxMessagesPerBundle,
        long maxPeerEndpoints,
        long maxQueueDepth,
        long maxSyncBytes) {

    static @Nullable ClprThrottleValues from(com.hederahashgraph.api.proto.java.ClprLedgerConfiguration configuration) {
        if (!configuration.hasThrottles()) {
            return null;
        }

        var throttles = configuration.getThrottles();
        return new ClprThrottleValues(
                throttles.getMaxGasPerMessage(),
                (long) throttles.getMaxLocalEndpoints(),
                (long) throttles.getMaxMessagePayloadBytes(),
                (long) throttles.getMaxMessagesPerBundle(),
                (long) throttles.getMaxPeerEndpoints(),
                (long) throttles.getMaxQueueDepth(),
                throttles.getMaxSyncBytes());
    }
}
