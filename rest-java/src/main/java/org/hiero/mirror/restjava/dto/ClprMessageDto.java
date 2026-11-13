// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import lombok.Builder;

/**
 * Record-based projection of a message. Spring Data JPA maps the query column aliases to the record components by
 * position, so the components match the SELECT clause order, which is sorted alphabetically by alias. The message type
 * and reply status are text, and {@code acknowledged} is computed from the owning channel's {@code acked_message_id}.
 * Only Data rows have a {@code connectorId}, {@code sender} and {@code targetApplication}, and only Response rows have
 * a {@code replyStatus} and {@code replyToMessageId}.
 */
@Builder
public record ClprMessageDto(
        boolean acknowledged,
        byte[] channelId,
        byte[] connectorId,
        long consensusTimestamp,
        byte[] messageData,
        long messageId,
        String messageType,
        String replyStatus,
        Long replyToMessageId,
        byte[] runningHash,
        byte[] sender,
        byte[] targetApplication) {}
