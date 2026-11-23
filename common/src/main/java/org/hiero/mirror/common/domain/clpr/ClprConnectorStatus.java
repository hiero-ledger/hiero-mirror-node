// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/**
 * Lifecycle state of a {@link ClprConnector} row. {@code ClprRegisterConnector} creates the row directly in
 * {@code ACTIVE} status, so there is no pre-creation {@code PENDING} state.
 */
public enum ClprConnectorStatus {
    ACTIVE,
    DELETED
}
