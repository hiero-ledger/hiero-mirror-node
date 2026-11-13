// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageReplyStatus;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
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
        final var channelId = domainBuilder.bytes(32);
        persist(channelId, 100L, 1L, ClprMessageType.DATA);
        final var inRange = persist(channelId, 200L, 2L, ClprMessageType.DATA);
        persist(channelId, 300L, 3L, ClprMessageType.DATA);
        persist(domainBuilder.bytes(32), 200L, 1L, ClprMessageType.DATA); // another channel

        // when
        final var actual =
                repository.findByChannelIdAndTimestampBetween(channelId, 150L, 250L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).extracting(ClprMessageDto::messageId).containsExactly(inRange.getMessageId());
    }

    @Test
    void findByChannelIdAndTimestampBetweenInclusiveBounds() {
        // given
        final var channelId = domainBuilder.bytes(32);
        persist(channelId, 100L, 1L, ClprMessageType.DATA);
        persist(channelId, 200L, 2L, ClprMessageType.DATA);

        // when
        final var actual =
                repository.findByChannelIdAndTimestampBetween(channelId, 100L, 200L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).extracting(ClprMessageDto::messageId).containsExactly(1L, 2L);
    }

    @Test
    void findByChannelIdAndTimestampBetweenOrderAndLimit() {
        // given
        final var channelId = domainBuilder.bytes(32);
        persist(channelId, 100L, 1L, ClprMessageType.DATA);
        persist(channelId, 200L, 2L, ClprMessageType.DATA);
        persist(channelId, 300L, 3L, ClprMessageType.DATA);

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
        final var channelId = domainBuilder.bytes(32);
        persist(channelId, 100L, 1L, ClprMessageType.DATA);
        persist(channelId, 200L, 2L, ClprMessageType.RESPONSE);
        persist(channelId, 300L, 3L, ClprMessageType.CONTROL);

        // when / then
        assertThat(repository.findByChannelIdAndTimestampBetween(
                        channelId, 0L, 1000L, "RESPONSE", page(Direction.ASC, 10)))
                .extracting(ClprMessageDto::messageId)
                .containsExactly(2L);
        assertThat(repository.findByChannelIdAndTimestampBetween(channelId, 0L, 1000L, null, page(Direction.ASC, 10)))
                .hasSize(3);
    }

    @Test
    void findByChannelIdAndConnectorIdAndTimestampBetween() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var connectorId = domainBuilder.bytes(32);
        final var match = persist(channelId, 100L, 1L, ClprMessageType.DATA, connectorId);
        persist(channelId, 200L, 2L, ClprMessageType.DATA, domainBuilder.bytes(32)); // another connector
        persist(channelId, 300L, 3L, ClprMessageType.RESPONSE, null); // no connector

        // when
        final var actual = repository.findByChannelIdAndConnectorIdAndTimestampBetween(
                channelId, connectorId, 0L, 1000L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).extracting(ClprMessageDto::messageId).containsExactly(match.getMessageId());
        assertThat(repository.findByChannelIdAndConnectorIdAndTimestampBetween(
                        channelId, connectorId, 0L, 1000L, "RESPONSE", page(Direction.ASC, 10)))
                .isEmpty();
    }

    @Test
    void mapsColumns() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var message = domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(channelId)
                        .consensusTimestamp(100L)
                        .controlMessage(null)
                        .messageId(7L)
                        .messageType(ClprMessageType.RESPONSE)
                        .replyStatus(ClprMessageReplyStatus.APPLICATION_ERROR)
                        .replyToMessageId(4L))
                .persist();

        // when
        final var actual =
                repository.findByChannelIdAndTimestampBetween(channelId, 0L, 1000L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).singleElement().satisfies(dto -> {
            assertThat(dto.acknowledged()).isFalse(); // unknown channel is never acknowledged
            assertThat(dto.channelId()).isEqualTo(channelId);
            assertThat(dto.connectorId()).isEqualTo(message.getConnectorId());
            assertThat(dto.consensusTimestamp()).isEqualTo(100L);
            assertThat(dto.messageData()).isEqualTo(message.getMessageData());
            assertThat(dto.messageId()).isEqualTo(7L);
            assertThat(dto.messageType()).isEqualTo("RESPONSE");
            assertThat(dto.replyStatus()).isEqualTo("APPLICATION_ERROR");
            assertThat(dto.replyToMessageId()).isEqualTo(4L);
            assertThat(dto.runningHash()).isEqualTo(message.getRunningHash());
            assertThat(dto.sender()).isEqualTo(message.getSender());
            assertThat(dto.targetApplication()).isEqualTo(message.getTargetApplication());
        });
    }

    @Test
    void acknowledgedFromChannelAckedMessageId() {
        // given
        final var channelId = domainBuilder.bytes(32);
        final var builder = ClprChannel.builder()
                .ackedMessageId(2L)
                .chainId("eip155:1")
                .channelId(channelId)
                .createdTimestamp(1L)
                .endpointManifestVersion(0L)
                .nextMessageId(4L)
                .ownershipCommitment(domainBuilder.bytes(32))
                .receivedMessageId(0L)
                .status(ClprChannelStatus.ACTIVE)
                .timestampRange(Range.atLeast(1L));
        domainBuilder.wrap(builder, builder::build).persist();
        persist(channelId, 100L, 1L, ClprMessageType.DATA);
        persist(channelId, 200L, 2L, ClprMessageType.DATA);
        persist(channelId, 300L, 3L, ClprMessageType.DATA);

        // when
        final var actual =
                repository.findByChannelIdAndTimestampBetween(channelId, 0L, 1000L, null, page(Direction.ASC, 10));

        // then
        assertThat(actual).extracting(ClprMessageDto::acknowledged).containsExactly(true, true, false);
    }

    private static PageRequest page(Direction direction, int limit) {
        return PageRequest.of(0, limit, Sort.by(direction, CONSENSUS_TIMESTAMP));
    }

    private ClprMessage persist(byte[] channelId, long timestamp, long messageId, ClprMessageType type) {
        return persist(channelId, timestamp, messageId, type, domainBuilder.bytes(32));
    }

    private ClprMessage persist(
            byte[] channelId, long timestamp, long messageId, ClprMessageType type, byte @Nullable [] connectorId) {
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
