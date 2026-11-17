// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/**
 * Lifecycle state of a {@link ClprConnector} row. Unlike {@link ClprChannelStatus}, where {@code PENDING} is an
 * unexpected state consensus should never report for an existing row, {@code PENDING} is the normal pre-creation
 * state here, since {@code ClprRegisterConnector} carries {@code channel_id} directly and so can be written as a
 * real row from the start rather than needing a separate pending-commitment table.
 */
public enum ClprConnectorStatus {
    PENDING,
    ACTIVE,
    DELETED
}
