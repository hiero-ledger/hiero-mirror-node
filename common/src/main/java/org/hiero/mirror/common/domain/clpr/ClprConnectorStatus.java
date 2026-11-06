// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/**
 * Lifecycle state of a {@link ClprConnector} row. Unlike {@link ClprChannelStatus}, {@code PENDING} is represented
 * here rather than by a separate pending-commitment table, since {@code ClprRegisterConnector} carries {@code
 * channel_id} directly and so can be written as a real row from the start.
 */
public enum ClprConnectorStatus {
    PENDING,
    ACTIVE,
    DELETED
}
