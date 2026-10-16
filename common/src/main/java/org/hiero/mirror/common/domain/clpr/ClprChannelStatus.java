// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/**
 * Mirrors the real {@code ClprChannelStatus} proto enum minus {@code PENDING}: unrevealed Channel commitments are
 * persisted in {@link ClprChannelPendingCommitment} instead, so {@code clpr_channel} itself never holds a row in
 * that state.
 */
public enum ClprChannelStatus {
    ACTIVE,
    PAUSED,
    CLOSING,
    DRAINED,
    CLOSED
}
