// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.rest.model.ClprMessageType;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.hiero.mirror.restjava.dto.ClprMessagesRequest;
import org.hiero.mirror.restjava.mapper.ClprChannelMapperImpl;
import org.hiero.mirror.restjava.mapper.ClprMessageMapperImpl;
import org.hiero.mirror.restjava.mapper.CommonMapperImpl;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.hiero.mirror.restjava.repository.ClprMessageRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

final class ClprServiceTest {

    private static final byte[] CHANNEL_ID = new byte[] {1, 2, 3};
    private static final byte[] CONNECTOR_ID = new byte[] {4, 5, 6};
    private static final String CONSENSUS_TIMESTAMP = "consensusTimestamp";
    private static final String CREATED_TIMESTAMP = "createdTimestamp";

    private ClprChannelRepository channelRepository;
    private ClprMessageRepository messageRepository;
    private final RestJavaProperties properties = new RestJavaProperties();
    private ClprService service;

    @BeforeEach
    void setup() {
        channelRepository = mock(ClprChannelRepository.class);
        final var channelMapper = new ClprChannelMapperImpl(new CommonMapperImpl());
        messageRepository = mock(ClprMessageRepository.class);
        final var commonMapper = new CommonMapperImpl();
        service = new ClprServiceImpl(
                channelMapper,
                channelRepository,
                new ClprMessageMapperImpl(commonMapper),
                messageRepository,
                properties);
    }

    @Test
    void getChannelsByIdWithoutTimestamp() {
        // given
        final var channel = channel("ACTIVE");
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(channel);
        final var request = ClprChannelsRequest.builder().channelId(CHANNEL_ID).build();

        // when / then
        assertThat(service.getChannels(request)).singleElement().satisfies(c -> {
            assertThat(c.getChannelId()).isEqualTo("0x010203");
            assertThat(c.getStatus()).isEqualTo(ClprChannelStatus.ACTIVE);
        });
    }

    @Test
    void getChannelsByIdWithoutTimestampFiltersByStatus() {
        // given
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(channel("ACTIVE"));

        // when / then
        assertThat(service.getChannels(ClprChannelsRequest.builder()
                        .channelId(CHANNEL_ID)
                        .status(ClprChannelStatus.ACTIVE)
                        .build()))
                .hasSize(1);
        assertThat(service.getChannels(ClprChannelsRequest.builder()
                        .channelId(CHANNEL_ID)
                        .status(ClprChannelStatus.PAUSED)
                        .build()))
                .isEmpty();
    }

    @Test
    void getChannelsByIdNotFound() {
        // given
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(null);

        // when / then
        assertThat(service.getChannels(
                        ClprChannelsRequest.builder().channelId(CHANNEL_ID).build()))
                .isEmpty();
    }

    @Test
    void getChannelsByIdAsOfTimestamp() {
        // given: eq becomes lte, so the as-of range is (0, 150]
        when(channelRepository.findByChannelIdAndTimestampBetween(CHANNEL_ID, 0L, 150L, null))
                .thenReturn(List.of(channel("PAUSED")));
        final var request = ClprChannelsRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.EQ, 150L)))
                .build();

        // when / then
        assertThat(service.getChannels(request))
                .singleElement()
                .satisfies(c -> assertThat(c.getStatus()).isEqualTo(ClprChannelStatus.PAUSED));
    }

    @Test
    void getChannelsByIdAsOfTimestampWithStatus() {
        // given
        final var request = ClprChannelsRequest.builder()
                .channelId(CHANNEL_ID)
                .status(ClprChannelStatus.PAUSED)
                .timestamp(timestamp(param(RangeOperator.LTE, 150L)))
                .build();

        // when
        service.getChannels(request);

        // then
        verify(channelRepository).findByChannelIdAndTimestampBetween(CHANNEL_ID, 0L, 150L, "PAUSED");
    }

    @Test
    void getChannelsList() {
        // given
        final var page = PageRequest.of(0, 25, Sort.by(Direction.DESC, CREATED_TIMESTAMP));
        final var items = List.of(
                item(CHANNEL_ID, 3_000L, "ACTIVE"),
                item(null, 2_000L, "PENDING"),
                item(new byte[] {9, 9}, 1_000L, "CLOSED"));
        when(channelRepository.findAllChannelsByCreatedTimestampBetween(0L, Long.MAX_VALUE, page))
                .thenReturn(items);

        // when / then
        assertThat(service.getChannels(ClprChannelsRequest.builder().build()))
                .extracting(c -> c.getStatus())
                .containsExactly(ClprChannelStatus.ACTIVE, ClprChannelStatus.PENDING, ClprChannelStatus.CLOSED);
    }

    @Test
    void getChannelsListRejectsRangeOverMaximumWithoutStatus() {
        // given
        final long max = properties.getMaxClprChannelsTimestampRange().toNanos();
        final var request = ClprChannelsRequest.builder()
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 101L + max)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getChannelsListWithBoundsStatusOrderAndLimit() {
        // given: gt/lt are converted to inclusive bounds
        final var request = ClprChannelsRequest.builder()
                .limit(10)
                .order(Direction.ASC)
                .status(ClprChannelStatus.CLOSED)
                .timestamp(timestamp(param(RangeOperator.GT, 100L), param(RangeOperator.LT, 200L)))
                .build();

        // when
        service.getChannels(request);

        // then
        verify(channelRepository)
                .findByCreatedTimestampBetween(
                        101L, 199L, "CLOSED", PageRequest.of(0, 10, Sort.by(Direction.ASC, CREATED_TIMESTAMP)));
    }

    @Test
    void getChannelsRejectsInvalidRange() {
        // given
        final var request = ClprChannelsRequest.builder()
                .timestamp(timestamp(param(RangeOperator.GTE, 200L), param(RangeOperator.LTE, 100L)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(channelRepository);
    }

    @Test
    void getChannelsRejectsNotEqualsOperator() {
        // given
        final var request = ClprChannelsRequest.builder()
                .timestamp(timestamp(param(RangeOperator.NE, 100L)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(channelRepository);
    }

    @Test
    void getChannelsRejectsRepeatedOperator() {
        // given
        final var request = ClprChannelsRequest.builder()
                .timestamp(timestamp(param(RangeOperator.GT, 100L), param(RangeOperator.GTE, 200L)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(channelRepository);
    }

    @Test
    void getChannelsPending() {
        // given
        final var pending = item(null, 1_000_000_000L, "PENDING", new byte[] {(byte) 0xab, (byte) 0xcd});
        final var page = PageRequest.of(0, 25, Sort.by(Direction.DESC, CREATED_TIMESTAMP));
        when(channelRepository.findPendingByCreatedTimestampBetween(0L, Long.MAX_VALUE, page))
                .thenReturn(List.of(pending));
        final var request =
                ClprChannelsRequest.builder().status(ClprChannelStatus.PENDING).build();

        // when / then
        assertThat(service.getChannels(request)).singleElement().satisfies(c -> {
            assertThat(c.getStatus()).isEqualTo(ClprChannelStatus.PENDING);
            assertThat(c.getOwnershipCommitment()).isEqualTo("0xabcd");
            assertThat(c.getCreatedTimestamp()).isEqualTo("1.000000000");
            assertThat(c.getChannelId()).isNull();
        });
    }

    @Test
    void getChannelsPendingWithBoundsOrderAndLimit() {
        // given
        final var request = ClprChannelsRequest.builder()
                .limit(5)
                .order(Direction.ASC)
                .status(ClprChannelStatus.PENDING)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 200L)))
                .build();

        // when
        service.getChannels(request);

        // then
        verify(channelRepository)
                .findPendingByCreatedTimestampBetween(
                        100L, 200L, PageRequest.of(0, 5, Sort.by(Direction.ASC, CREATED_TIMESTAMP)));
    }

    @Test
    void getChannelsPendingRejectsChannelId() {
        // given
        final var request = ClprChannelsRequest.builder()
                .channelId(CHANNEL_ID)
                .status(ClprChannelStatus.PENDING)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(channelRepository);
    }

    @Test
    void getChannelsPendingRejectsInvalidRange() {
        // given
        final var request = ClprChannelsRequest.builder()
                .status(ClprChannelStatus.PENDING)
                .timestamp(timestamp(param(RangeOperator.GTE, 200L), param(RangeOperator.LTE, 100L)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(channelRepository);
    }

    @Test
    void getChannelsRejectsRangeOverMaximum() {
        // given: 30 days is allowed, one nanosecond more isn't
        final long max = properties.getMaxClprChannelsTimestampRange().toNanos();
        final var allowed = ClprChannelsRequest.builder()
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 100L + max)))
                .build();
        final var tooLong = ClprChannelsRequest.builder()
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 101L + max)))
                .build();

        // when / then
        service.getChannels(allowed);
        assertThatThrownBy(() -> service.getChannels(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getChannelsRejectsPendingRangeOverMaximum() {
        // given
        final long max = properties.getMaxClprChannelsTimestampRange().toNanos();
        final var request = ClprChannelsRequest.builder()
                .status(ClprChannelStatus.PENDING)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 101L + max)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(channelRepository);
    }

    @Test
    void getChannelsByIdIgnoresMaximumRange() {
        // given
        final long max = properties.getMaxClprChannelsTimestampRange().toNanos();
        final var request = ClprChannelsRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 101L + max)))
                .build();

        // when
        service.getChannels(request);

        // then
        verify(channelRepository).findByChannelIdAndTimestampBetween(CHANNEL_ID, 100L, 101L + max, null);
    }

    @Test
    void getMessages() {
        // given
        final var page = PageRequest.of(0, 25, Sort.by(Direction.DESC, CONSENSUS_TIMESTAMP));
        when(messageRepository.findByChannelIdAndTimestampBetween(CHANNEL_ID, 100L, 200L, null, page))
                .thenReturn(List.of(message(150L, "DATA")));
        final var request = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 200L)))
                .build();

        // when / then
        assertThat(service.getMessages(request)).singleElement().satisfies(m -> {
            assertThat(m.getChannelId()).isEqualTo("0x010203");
            assertThat(m.getTimestamp()).isEqualTo("0.000000150");
            assertThat(m.getType()).isEqualTo(ClprMessageType.DATA);
        });
        verifyNoInteractions(channelRepository);
    }

    @Test
    void getMessagesByConnectorAndType() {
        // given
        final var page = PageRequest.of(0, 10, Sort.by(Direction.ASC, CONSENSUS_TIMESTAMP));
        when(messageRepository.findByChannelIdAndConnectorIdAndTimestampBetween(
                        CHANNEL_ID, CONNECTOR_ID, 100L, 200L, "DATA", page))
                .thenReturn(List.of(message(150L, "DATA")));
        final var request = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .connectorId(CONNECTOR_ID)
                .limit(10)
                .order(Direction.ASC)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 200L)))
                .type(ClprMessageType.DATA)
                .build();

        // when / then
        assertThat(service.getMessages(request)).hasSize(1);
        verify(messageRepository)
                .findByChannelIdAndConnectorIdAndTimestampBetween(CHANNEL_ID, CONNECTOR_ID, 100L, 200L, "DATA", page);
    }

    @Test
    void getMessagesWithExclusiveBounds() {
        // given
        final var request = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.GT, 100L), param(RangeOperator.LT, 200L)))
                .build();

        // when
        service.getMessages(request);

        // then
        verify(messageRepository)
                .findByChannelIdAndTimestampBetween(
                        CHANNEL_ID,
                        101L,
                        199L,
                        null,
                        PageRequest.of(0, 25, Sort.by(Direction.DESC, CONSENSUS_TIMESTAMP)));
    }

    @Test
    void getMessagesRequiresTimestamp() {
        // given
        final var request = ClprMessagesRequest.builder().channelId(CHANNEL_ID).build();

        // when / then
        assertThatThrownBy(() -> service.getMessages(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messageRepository);
    }

    @Test
    void getMessagesRejectsRangeOverMaximum() {
        // given
        final long max = properties.getMaxClprMessagesTimestampRange().toNanos();
        final var allowed = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 100L + max)))
                .build();
        final var tooLong = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 101L + max)))
                .build();

        // when / then
        service.getMessages(allowed);
        assertThatThrownBy(() -> service.getMessages(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getMessagesRejectsInvalidTimestamp() {
        // given
        final var notEqual = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.NE, 100L)))
                .build();
        final var inverted = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.GTE, 200L), param(RangeOperator.LTE, 100L)))
                .build();

        // when / then
        assertThatThrownBy(() -> service.getMessages(notEqual)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getMessages(inverted)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messageRepository);
    }

    private static ClprMessageDto message(long consensusTimestamp, String type) {
        return ClprMessageDto.builder()
                .channelId(CHANNEL_ID)
                .consensusTimestamp(consensusTimestamp)
                .messageId(1L)
                .messageType(type)
                .build();
    }

    private static ClprChannelDto channel(String status) {
        return item(CHANNEL_ID, 0L, status);
    }

    private static TimestampParameter param(RangeOperator operator, long value) {
        return new TimestampParameter(operator, value);
    }

    private static Bound timestamp(TimestampParameter... params) {
        return Bound.of(params, "timestamp", null);
    }

    private static ClprChannelDto item(byte @Nullable [] channelId, long created, String status) {
        return item(channelId, created, status, new byte[] {1});
    }

    private static ClprChannelDto item(
            byte @Nullable [] channelId, long created, String status, byte[] ownershipCommitment) {
        return ClprChannelDto.builder()
                .channelId(channelId)
                .createdTimestamp(created)
                .ownershipCommitment(ownershipCommitment)
                .status(status)
                .build();
    }
}
