// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/**
 * Mirrors the real {@code ClprChannelStatus} proto enum. A channel's pre-creation {@code PENDING} state is normally
 * tracked via a {@link ClprChannelPendingCommitment} row instead -- consensus should never revert an existing
 * {@link ClprChannel} state change back to {@code PENDING} -- but the value is still included here so that an
 * unexpected occurrence can be recorded rather than dropped.
 */
public enum ClprChannelStatus {
    PENDING,
    ACTIVE,
    PAUSED,
    CLOSING,
    DRAINED,
    CLOSED
}
