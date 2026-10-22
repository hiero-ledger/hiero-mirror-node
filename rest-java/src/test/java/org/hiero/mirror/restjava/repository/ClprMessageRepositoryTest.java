// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hiero.mirror.restjava.jooq.domain.tables.ClprMessage.CLPR_MESSAGE;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageReplyStatus;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.hiero.mirror.restjava.dto.ClprMessagesRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprMessageRepositoryTest extends RestJavaIntegrationTest {

    private static final byte[] CHANNEL_ID = new byte[32];
    private static final long ACKED_MESSAGE_ID = 2L;

    private final ClprMessageRepository repository;

    @BeforeEach
    void setup() {
        persistChannel(CHANNEL_ID, ACKED_MESSAGE_ID);
    }

    @Test
    void findAllMapsAllColumns() {
        // given
        final var connectorId = domainBuilder.bytes(32);
        final var message = domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(CHANNEL_ID)
                        .connectorId(connectorId)
                        .consensusTimestamp(100L)
                        .messageData(new byte[] {1, 2, 3})
                        .messageId(1L)
                        .messageType(ClprMessageType.RESPONSE)
                        .replyStatus(ClprMessageReplyStatus.APPLICATION_ERROR)
                        .replyToMessageId(7L)
                        .runningHash(domainBuilder.bytes(32))
                        .sender(domainBuilder.bytes(20))
                        .targetApplication(domainBuilder.bytes(24)))
                .persist();

        // when
        final var actual = find(request().build());

        // then
        assertThat(actual).singleElement().satisfies(dto -> {
            assertThat(dto.acknowledged()).isTrue();
            assertThat(dto.channelId()).isEqualTo(CHANNEL_ID);
            assertThat(dto.connectorId()).isEqualTo(connectorId);
            assertThat(dto.consensusTimestamp()).isEqualTo(100L);
            assertThat(dto.messageData()).isEqualTo(new byte[] {1, 2, 3});
            assertThat(dto.messageId()).isEqualTo(1L);
            assertThat(dto.messageType()).isEqualTo("RESPONSE");
            assertThat(dto.replyStatus()).isEqualTo("APPLICATION_ERROR");
            assertThat(dto.replyToMessageId()).isEqualTo(7L);
            assertThat(dto.runningHash()).isEqualTo(message.getRunningHash());
            assertThat(dto.sender()).isEqualTo(message.getSender());
            assertThat(dto.targetApplication()).isEqualTo(message.getTargetApplication());
        });
    }

    @Test
    void findAllNullableColumns() {
        // given
        domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(CHANNEL_ID)
                        .connectorId(null)
                        .messageData(null)
                        .replyStatus(null)
                        .replyToMessageId(null)
                        .sender(null)
                        .targetApplication(null))
                .persist();

        // when
        final var actual = find(request().build());

        // then
        assertThat(actual).singleElement().satisfies(dto -> {
            assertThat(dto.connectorId()).isNull();
            assertThat(dto.messageData()).isNull();
            assertThat(dto.replyStatus()).isNull();
            assertThat(dto.replyToMessageId()).isNull();
            assertThat(dto.sender()).isNull();
            assertThat(dto.targetApplication()).isNull();
        });
    }

    @Test
    void findAllAcknowledged() {
        // given the channel's acked_message_id is 2
        persist(100L, 1L);
        persist(200L, 2L);
        persist(300L, 3L);

        // when
        final var actual = find(request().order(Direction.ASC).build());

        // then
        assertThat(actual)
                .extracting(ClprMessageDto::messageId, ClprMessageDto::acknowledged)
                .containsExactly(tuple(1L, true), tuple(2L, true), tuple(3L, false));
    }

    @Test
    void findAllWithoutChannelRow() {
        // given messages whose channel has no row, so the join yields nothing
        final var otherChannel = domainBuilder.bytes(32);
        persistFor(otherChannel, 100L, 1L);

        // when
        final var actual =
                find(ClprMessagesRequest.builder().channelId(otherChannel).build());

        // then
        assertThat(actual).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findAllByChannelId(Direction order) {
        // given
        final var message1 = persist(100L, 1L);
        final var message2 = persist(200L, 2L);
        final var otherChannel = domainBuilder.bytes(32);
        persistChannel(otherChannel, 0L);
        persistFor(otherChannel, 150L, 3L);
        final var expected = order.isAscending() ? List.of(message1, message2) : List.of(message2, message1);

        // when
        final var actual = find(request().order(order).build());

        // then
        assertThat(ids(actual)).containsExactlyElementsOf(ids(expected));
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void findAllTiebreaksByMessageId(Direction order) {
        // given
        final var message1 = persist(100L, 1L);
        final var message2 = persist(100L, 2L);
        final var message3 = persist(100L, 3L);
        final var later = persist(200L, 4L);
        final var expected = order.isAscending()
                ? List.of(message1, message2, message3, later)
                : List.of(later, message3, message2, message1);

        // when
        final var actual = find(request().order(order).build());

        // then
        assertThat(ids(actual)).containsExactlyElementsOf(ids(expected));
    }

    @Test
    void findAllLimit() {
        // given
        persist(100L, 1L);
        final var message2 = persist(200L, 2L);
        final var message3 = persist(300L, 3L);

        // when
        final var actual = find(request().limit(2).build());

        // then
        assertThat(ids(actual)).containsExactly(message3.getMessageId(), message2.getMessageId());
    }

    @Test
    void findAllByConnectorId() {
        // given
        final var connectorId = domainBuilder.bytes(32);
        final var message1 = persistFor(CHANNEL_ID, connectorId, 100L, 1L, ClprMessageType.DATA);
        final var message2 = persistFor(CHANNEL_ID, connectorId, 200L, 2L, ClprMessageType.DATA);
        persist(300L, 3L); // other connector
        persistFor(CHANNEL_ID, null, 400L, 4L, ClprMessageType.RESPONSE); // no connector

        // when
        final var actual = find(request().connectorId(connectorId).build());

        // then
        assertThat(ids(actual)).containsExactly(message2.getMessageId(), message1.getMessageId());
        assertThat(actual).extracting(ClprMessageDto::acknowledged).containsExactly(true, true);
    }

    @ParameterizedTest
    @EnumSource(ClprMessageType.class)
    void findAllByType(ClprMessageType type) {
        // given
        final var expected = persistFor(CHANNEL_ID, null, 100L, 1L, type);
        for (final var other : ClprMessageType.values()) {
            if (other != type) {
                persistFor(CHANNEL_ID, null, 200L, 2L + other.ordinal(), other);
            }
        }

        // when
        final var actual = find(request().type(type).build());

        // then
        assertThat(ids(actual)).containsExactly(expected.getMessageId());
    }

    @Test
    void findAllByTimestampRange() {
        // given
        persist(100L, 1L);
        final var message2 = persist(200L, 2L);
        final var message3 = persist(300L, 3L);
        persist(400L, 4L);

        // when
        final var inclusive = find(
                request().timestampLowerBound(200L).timestampUpperBound(300L).build());
        final var exclusive = find(
                request().timestampLowerBound(101L).timestampUpperBound(399L).build());

        // then
        assertThat(ids(inclusive)).containsExactly(message3.getMessageId(), message2.getMessageId());
        assertThat(ids(exclusive)).containsExactly(message3.getMessageId(), message2.getMessageId());
    }

    @Test
    void findAllByTimestampEq() {
        // given
        persist(100L, 1L);
        final var message2 = persist(200L, 2L);

        // when
        final var actual = find(
                request().timestampLowerBound(200L).timestampUpperBound(200L).build());

        // then
        assertThat(ids(actual)).containsExactly(message2.getMessageId());
    }

    @Test
    void findAllCompoundBoundDescending() {
        // given a cursor of timestamp=lte:100 and message.id=lt:3, i.e. the next page after (100, 3)
        final var earlier = persist(50L, 9L);
        final var message1 = persist(100L, 1L);
        final var message2 = persist(100L, 2L);
        persist(100L, 3L);
        persist(200L, 4L);
        final var request =
                request().messageIdUpperBound(2L).timestampUpperBound(100L).build();

        // when
        final var actual = find(request);

        // then
        assertThat(ids(actual))
                .containsExactly(message2.getMessageId(), message1.getMessageId(), earlier.getMessageId());
    }

    @Test
    void findAllCompoundBoundAscending() {
        // given a cursor of timestamp=gte:100 and message.id=gt:1, i.e. the next page after (100, 1)
        persist(50L, 9L);
        persist(100L, 1L);
        final var message2 = persist(100L, 2L);
        final var message3 = persist(100L, 3L);
        final var later = persist(200L, 4L);
        final var request = request()
                .messageIdLowerBound(2L)
                .order(Direction.ASC)
                .timestampLowerBound(100L)
                .build();

        // when
        final var actual = find(request);

        // then
        assertThat(ids(actual)).containsExactly(message2.getMessageId(), message3.getMessageId(), later.getMessageId());
    }

    @Test
    void findAllCombinedFilters() {
        // given
        final var connectorId = domainBuilder.bytes(32);
        final var expected = persistFor(CHANNEL_ID, connectorId, 200L, 2L, ClprMessageType.DATA);
        persistFor(CHANNEL_ID, connectorId, 100L, 1L, ClprMessageType.DATA); // out of range
        persistFor(CHANNEL_ID, domainBuilder.bytes(32), 200L, 3L, ClprMessageType.DATA); // other connector
        persistFor(domainBuilder.bytes(32), connectorId, 200L, 4L, ClprMessageType.DATA); // other channel
        final var request = request()
                .connectorId(connectorId)
                .timestampLowerBound(150L)
                .timestampUpperBound(250L)
                .type(ClprMessageType.DATA)
                .build();

        // when / then
        assertThat(ids(find(request))).containsExactly(expected.getMessageId());
    }

    @Test
    void findAllEmpty() {
        assertThat(find(request().build())).isEmpty();
    }

    @Test
    void findById() {
        // given
        final var message = persist(100L, 1L);
        persist(200L, 2L);
        final var id = new ClprMessage.Id(CHANNEL_ID, message.getMessageId());

        // when / then
        assertThat(repository.findById(id)).contains(message);
    }

    private List<ClprMessageDto> find(ClprMessagesRequest request) {
        final var sort = Sort.by(
                request.getOrder(), CLPR_MESSAGE.CONSENSUS_TIMESTAMP.getName(), CLPR_MESSAGE.MESSAGE_ID.getName());
        final var page = PageRequest.of(0, request.getLimit(), sort);
        final var type = request.getType() == null ? null : request.getType().name();
        final var connectorId = request.getConnectorId();
        if (connectorId == null) {
            return repository.findByChannelId(
                    request.getChannelId(),
                    request.getTimestampLowerBound(),
                    request.getTimestampUpperBound(),
                    request.getMessageIdLowerBound(),
                    request.getMessageIdUpperBound(),
                    type,
                    page);
        }

        return repository.findByChannelIdAndConnectorId(
                request.getChannelId(),
                connectorId,
                request.getTimestampLowerBound(),
                request.getTimestampUpperBound(),
                request.getMessageIdLowerBound(),
                request.getMessageIdUpperBound(),
                type,
                page);
    }

    private static List<Long> ids(List<?> messages) {
        return messages.stream()
                .map(m -> m instanceof ClprMessageDto dto ? dto.messageId() : ((ClprMessage) m).getMessageId())
                .toList();
    }

    private ClprMessagesRequest.ClprMessagesRequestBuilder request() {
        return ClprMessagesRequest.builder().channelId(CHANNEL_ID);
    }

    private void persistChannel(byte[] channelId, long ackedMessageId) {
        domainBuilder
                .clprChannel()
                .customize(c -> c.ackedMessageId(ackedMessageId).channelId(channelId))
                .persist();
    }

    private ClprMessage persist(long consensusTimestamp, long messageId) {
        return persistFor(CHANNEL_ID, consensusTimestamp, messageId);
    }

    private ClprMessage persistFor(byte[] channel, long consensusTimestamp, long messageId) {
        return persistFor(channel, domainBuilder.bytes(32), consensusTimestamp, messageId, ClprMessageType.DATA);
    }

    private ClprMessage persistFor(
            byte[] channel,
            byte @Nullable [] connectorId,
            long consensusTimestamp,
            long messageId,
            ClprMessageType type) {
        return domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(channel)
                        .connectorId(connectorId)
                        .consensusTimestamp(consensusTimestamp)
                        .messageId(messageId)
                        .messageType(type))
                .persist();
    }
}
