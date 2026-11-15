// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/**
 * Mirrors the real {@code ClprChannelStatus} proto enum minus {@code PENDING}, which is instead represented by a
 * {@link ClprChannelPendingCommitment} row.
 */
public enum ClprChannelStatus {
    ACTIVE,
    PAUSED,
    CLOSING,
    DRAINED,
    CLOSED
}
