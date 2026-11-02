// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/**
 * Lifecycle state of a {@link ClprChannelPendingCommitment}/{@link ClprConnectorPendingCommitment} row. The three
 * values are mutually exclusive by construction -- a commitment is registered, then exactly one of completed or
 * abandoned. {@code DELETED} is unused for connectors today: there's no abandonment path for a connector
 * commitment, only for a channel one (via {@code ClprCloseChannel}'s admin-sweep case).
 */
public enum ClprPendingCommitmentStatus {
    OPEN,
    COMPLETED,
    DELETED
}
