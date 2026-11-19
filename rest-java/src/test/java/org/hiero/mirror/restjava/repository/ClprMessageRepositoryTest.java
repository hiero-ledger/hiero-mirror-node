// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ClprChannelStatus;
import com.hederahashgraph.api.proto.java.ClprMessagePayload.PayloadCase;
import com.hederahashgraph.api.proto.java.ClprMessageReplyStatus;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprMessageRepositoryTest extends RestJavaIntegrationTest {

    private static final String CONSENSUS_TIMESTAMP = "consensusTimestamp";

    private final ClprMessageRepository repository;

    @Test
    void findByChannelIdAndTimestampBetween() {
        // given
        final var channelId = persistChannel(0L);
        persist(channelId, 100L, 1L, PayloadCase.MESSAGE.getNumber());
        final var inRange = persist(channelId, 200L, 2L, PayloadCase.MESSAGE.getNumber());
        persist(channelId, 300L, 3L, PayloadCase.MESSAGE.getNumber());
        persist(domainBuilder.bytes(32), 200L, 1L, PayloadCase.MESSAGE.getNumber()); // another channel

        // when
        final var actual =
                repository.findByChannelIdAndTimestampBetween(channelId, 150L, 250L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).extracting(ClprMessageDto::messageId).containsExactly(inRange.getMessageId());
    }

    @Test
    void findByChannelIdAndTimestampBetweenInclusiveBounds() {
        // given
        final var channelId = persistChannel(0L);
        persist(channelId, 100L, 1L, PayloadCase.MESSAGE.getNumber());
        persist(channelId, 200L, 2L, PayloadCase.MESSAGE.getNumber());

        // when
        final var actual =
                repository.findByChannelIdAndTimestampBetween(channelId, 100L, 200L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).extracting(ClprMessageDto::messageId).containsExactly(1L, 2L);
    }

    @Test
    void findByChannelIdAndTimestampBetweenOrderAndLimit() {
        // given
        final var channelId = persistChannel(0L);
        persist(channelId, 100L, 1L, PayloadCase.MESSAGE.getNumber());
        persist(channelId, 200L, 2L, PayloadCase.MESSAGE.getNumber());
        persist(channelId, 300L, 3L, PayloadCase.MESSAGE.getNumber());

        // when / then
        assertThat(repository.findByChannelIdAndTimestampBetween(channelId, 0L, 1000L, null, page(Direction.DESC, 2)))
                .extracting(ClprMessageDto::messageId)
                .containsExactly(3L, 2L);
        assertThat(repository.findByChannelIdAndTimestampBetween(channelId, 0L, 1000L, null, page(Direction.ASC, 2)))
                .extracting(ClprMessageDto::messageId)
                .containsExactly(1L, 2L);
    }

    @Test
    void findByChannelIdAndTimestampBetweenType() {
        // given
        final var channelId = persistChannel(0L);
        persist(channelId, 100L, 1L, PayloadCase.MESSAGE.getNumber());
        persist(channelId, 200L, 2L, PayloadCase.MESSAGE_REPLY.getNumber());
        persist(channelId, 300L, 3L, PayloadCase.CONTROL.getNumber());

        // when / then
        assertThat(repository.findByChannelIdAndTimestampBetween(
                        channelId, 0L, 1000L, PayloadCase.MESSAGE_REPLY.getNumber(), page(Direction.ASC, 10)))
                .extracting(ClprMessageDto::messageId)
                .containsExactly(2L);
        assertThat(repository.findByChannelIdAndTimestampBetween(channelId, 0L, 1000L, null, page(Direction.ASC, 10)))
                .hasSize(3);
    }

    @Test
    void findByChannelIdAndConnectorIdAndTimestampBetween() {
        // given
        final var channelId = persistChannel(0L);
        final var connectorId = domainBuilder.bytes(32);
        final var match = persist(channelId, 100L, 1L, PayloadCase.MESSAGE.getNumber(), connectorId);
        persist(channelId, 200L, 2L, PayloadCase.MESSAGE.getNumber(), domainBuilder.bytes(32)); // another connector
        persist(channelId, 300L, 3L, PayloadCase.MESSAGE_REPLY.getNumber(), null); // no connector

        // when
        final var actual = repository.findByChannelIdAndConnectorIdAndTimestampBetween(
                channelId, connectorId, 0L, 1000L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).extracting(ClprMessageDto::messageId).containsExactly(match.getMessageId());
        assertThat(repository.findByChannelIdAndConnectorIdAndTimestampBetween(
                        channelId,
                        connectorId,
                        0L,
                        1000L,
                        PayloadCase.MESSAGE_REPLY.getNumber(),
                        page(Direction.ASC, 10)))
                .isEmpty();
    }

    @Test
    void mapsColumns() {
        // given
        final var channelId = persistChannel(0L);
        final var message = domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(channelId)
                        .consensusTimestamp(100L)
                        .controlMessage(null)
                        .messageId(7L)
                        .messageType(PayloadCase.MESSAGE_REPLY.getNumber())
                        .replyStatus(ClprMessageReplyStatus.APPLICATION_ERROR_VALUE)
                        .replyToMessageId(4L))
                .persist();

        // when
        final var actual =
                repository.findByChannelIdAndTimestampBetween(channelId, 0L, 1000L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).singleElement().satisfies(dto -> {
            assertThat(dto.acknowledged()).isFalse();
            assertThat(dto.channelId()).isEqualTo(channelId);
            assertThat(dto.connectorId()).isEqualTo(message.getConnectorId());
            assertThat(dto.consensusTimestamp()).isEqualTo(100L);
            assertThat(dto.messageData()).isEqualTo(message.getMessageData());
            assertThat(dto.messageId()).isEqualTo(7L);
            assertThat(dto.messageType()).isEqualTo(PayloadCase.MESSAGE_REPLY.getNumber());
            assertThat(dto.replyStatus()).isEqualTo(ClprMessageReplyStatus.APPLICATION_ERROR_VALUE);
            assertThat(dto.replyToMessageId()).isEqualTo(4L);
            assertThat(dto.runningHash()).isEqualTo(message.getRunningHash());
            assertThat(dto.sender()).isEqualTo(message.getSender());
            assertThat(dto.targetApplication()).isEqualTo(message.getTargetApplication());
        });
    }

    @Test
    void acknowledgedFromChannelAckedMessageId() {
        // given
        final var channelId = persistChannel(2L);
        persist(channelId, 100L, 1L, PayloadCase.MESSAGE.getNumber());
        persist(channelId, 200L, 2L, PayloadCase.MESSAGE.getNumber());
        persist(channelId, 300L, 3L, PayloadCase.MESSAGE.getNumber());

        // when
        final var actual =
                repository.findByChannelIdAndTimestampBetween(channelId, 0L, 1000L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).extracting(ClprMessageDto::acknowledged).containsExactly(true, true, false);
    }

    /** Persists the channel that every message belongs to, and returns its id. */
    private byte[] persistChannel(long ackedMessageId) {
        final var channelId = domainBuilder.bytes(32);
        domainBuilder
                .clprChannel()
                .customize(c -> c.ackedMessageId(ackedMessageId)
                        .channelId(channelId)
                        .createdTimestamp(1L)
                        .nextMessageId(ackedMessageId + 2)
                        .status(ClprChannelStatus.ACTIVE_VALUE)
                        .timestampRange(Range.atLeast(1L)))
                .persist();
        return channelId;
    }

    private static PageRequest page(Direction direction, int limit) {
        return PageRequest.of(0, limit, Sort.by(direction, CONSENSUS_TIMESTAMP));
    }

    private ClprMessage persist(byte[] channelId, long timestamp, long messageId, int type) {
        return persist(channelId, timestamp, messageId, type, domainBuilder.bytes(32));
    }

    private ClprMessage persist(
            byte[] channelId, long timestamp, long messageId, int type, byte @Nullable [] connectorId) {
        return domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(channelId)
                        .connectorId(connectorId)
                        .consensusTimestamp(timestamp)
                        .controlMessage(null)
                        .messageId(messageId)
                        .messageType(type))
                .persist();
    }
}
