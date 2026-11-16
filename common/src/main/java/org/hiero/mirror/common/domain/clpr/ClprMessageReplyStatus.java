// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/** Mirrors the real {@code ClprMessageReplyStatus} proto enum. */
public enum ClprMessageReplyStatus {
    SUCCESS,
    APPLICATION_ERROR,
    CONNECTOR_NOT_FOUND,
    CONNECTOR_UNDERFUNDED
}
