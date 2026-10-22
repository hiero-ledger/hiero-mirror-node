// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import org.jspecify.annotations.NullUnmarked;

/**
 * Record-based projection of a {@code clpr_message} row joined with its channel, so {@code acknowledged}
 * ({@code message_id <= clpr_channel.acked_message_id}) is computed by the query. Spring Data JPA maps the query's
 * columns to the record components by position, so the order here matches the query's select order (alphabetical by
 * alias). The enum columns are kept as strings and converted by the mapper.
 */
@NullUnmarked
public record ClprMessageDto(
        Boolean acknowledged,
        byte[] channelId,
        byte[] connectorId,
        Long consensusTimestamp,
        byte[] messageData,
        Long messageId,
        String messageType,
        String replyStatus,
        Long replyToMessageId,
        byte[] runningHash,
        byte[] sender,
        byte[] targetApplication) {}
