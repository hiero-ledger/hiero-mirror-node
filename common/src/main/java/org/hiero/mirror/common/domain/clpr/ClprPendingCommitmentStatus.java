// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/** Lifecycle state of a {@link ClprChannelPendingCommitment} row. */
public enum ClprPendingCommitmentStatus {
    OPEN,
    COMPLETED,
    DELETED
}
