// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

/**
 * Mirrors the real {@code ClprMessageReplyStatus} proto enum minus {@code REDACTED} (message redaction was removed
 * from the HIP-1535 spec under Rejected Ideas, despite the reference proto still defining it) and
 * {@code CHANNEL_CLOSED} (reserved, never generated per the proto's own doc comment).
 */
public enum ClprMessageReplyStatus {
    SUCCESS,
    APPLICATION_ERROR,
    CONNECTOR_NOT_FOUND,
    CONNECTOR_UNDERFUNDED
}
