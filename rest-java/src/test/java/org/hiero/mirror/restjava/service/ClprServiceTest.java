// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.CLOSED_VALUE;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.PAUSED_VALUE;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.PENDING_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ClprMessagePayload.PayloadCase;
import java.util.List;
import java.util.stream.Stream;
import org.hiero.mirror.common.util.DomainUtils;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

final class ClprServiceTest {

    private static final int ACTIVE = com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE_VALUE;
    private static final byte[] CHANNEL_ID = new byte[] {1, 2, 3};
    private static final byte[] CONNECTOR_ID = new byte[] {4, 5, 6};
    private static final String CONSENSUS_TIMESTAMP = "consensusTimestamp";
    private static final String CREATED_TIMESTAMP = "createdTimestamp";
    private static final long LOWER = 100L;

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
        final var channel = channel(ACTIVE_VALUE);
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
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(channel(ACTIVE_VALUE));

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
                .thenReturn(List.of(channel(PAUSED_VALUE)));
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
        verify(channelRepository).findByChannelIdAndTimestampBetween(CHANNEL_ID, 0L, 150L, PAUSED_VALUE);
    }

    @Test
    void getChannelsList() {
        // given
        final var page = PageRequest.of(0, 25, Sort.by(Direction.DESC, CREATED_TIMESTAMP));
        final var items = List.of(
                item(CHANNEL_ID, 3_000L, ACTIVE_VALUE),
                item(null, 2_000L, PENDING_VALUE),
                item(new byte[] {9, 9}, 1_000L, CLOSED_VALUE));
        when(channelRepository.findAllChannelsByCreatedTimestampBetween(0L, Long.MAX_VALUE, page))
                .thenReturn(items);

        // when / then
        assertThat(service.getChannels(ClprChannelsRequest.builder().build()))
                .extracting(c -> c.getStatus())
                .containsExactly(ClprChannelStatus.ACTIVE, ClprChannelStatus.PENDING, ClprChannelStatus.CLOSED);
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
                .findByStatusAndCreatedTimestampBetween(
                        CLOSED_VALUE, 101L, 199L, PageRequest.of(0, 10, Sort.by(Direction.ASC, CREATED_TIMESTAMP)));
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
        final var pending = item(null, 1_000_000_000L, PENDING_VALUE, new byte[] {(byte) 0xab, (byte) 0xcd});
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
    void getMessages() {
        // given
        final var page = PageRequest.of(0, 25, Sort.by(Direction.DESC, CONSENSUS_TIMESTAMP));
        when(messageRepository.findByChannelIdAndTimestampBetween(CHANNEL_ID, 100L, 200L, null, page))
                .thenReturn(List.of(message(150L, PayloadCase.MESSAGE.getNumber())));
        final var request = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 200L)))
                .build();

        // when / then
        assertThat(service.getMessages(request).messages()).singleElement().satisfies(m -> {
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
                        CHANNEL_ID, CONNECTOR_ID, 100L, 200L, PayloadCase.MESSAGE.getNumber(), page))
                .thenReturn(List.of(message(150L, PayloadCase.MESSAGE.getNumber())));
        final var request = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .connectorId(CONNECTOR_ID)
                .limit(10)
                .order(Direction.ASC)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 200L)))
                .type(ClprMessageType.DATA)
                .build();

        // when / then
        assertThat(service.getMessages(request).messages()).hasSize(1);
        verify(messageRepository)
                .findByChannelIdAndConnectorIdAndTimestampBetween(
                        CHANNEL_ID, CONNECTOR_ID, 100L, 200L, PayloadCase.MESSAGE.getNumber(), page);
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
    void getMessagesDefaultDescendingReturnsLatestMaximumRangeAndResumesBeforeIt() {
        // given
        final long max = properties.getMaxClprMessagesTimestampRange().toNanos();
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(item(CHANNEL_ID, 1L, ACTIVE));
        final long before = DomainUtils.now();

        // when
        final var result = service.getMessages(
                ClprMessagesRequest.builder().channelId(CHANNEL_ID).build());

        // then
        final long after = DomainUtils.now();
        final var bounds = captureMessageBounds();
        assertThat(bounds.upper()).isBetween(before, after);
        assertThat(bounds.upper() - bounds.lower()).isEqualTo(max);
        assertThat(result.resumeTimestamp()).isEqualTo(bounds.lower());
    }

    @Test
    void getMessagesDefaultDescendingClampsToChannelCreation() {
        // given: the channel is younger than the maximum range, so the window starts at its creation
        final long created = DomainUtils.now() - 1_000_000_000L;
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(item(CHANNEL_ID, created, ACTIVE));

        // when
        final var result = service.getMessages(
                ClprMessagesRequest.builder().channelId(CHANNEL_ID).build());

        // then
        assertThat(captureMessageBounds().lower()).isEqualTo(created);
        assertThat(result.resumeTimestamp()).isNull();
    }

    @Test
    void getMessagesDefaultAscendingReturnsEarliestMaximumRangeAndResumesAfterIt() {
        // given
        final long max = properties.getMaxClprMessagesTimestampRange().toNanos();
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(item(CHANNEL_ID, 1L, ACTIVE));

        // when
        final var result = service.getMessages(ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .order(Direction.ASC)
                .build());

        // then
        final var bounds = captureMessageBounds();
        assertThat(bounds.lower()).isEqualTo(1L);
        assertThat(bounds.upper()).isEqualTo(1L + max);
        assertThat(result.resumeTimestamp()).isEqualTo(1L + max);
    }

    @Test
    void getMessagesDefaultAscendingClampsToNow() {
        // given: the channel is younger than the maximum range, so the window ends now
        final long created = DomainUtils.now() - 1_000_000_000L;
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(item(CHANNEL_ID, created, ACTIVE));
        final long before = DomainUtils.now();

        // when
        final var result = service.getMessages(ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .order(Direction.ASC)
                .build());

        // then
        final long after = DomainUtils.now();
        final var bounds = captureMessageBounds();
        assertThat(bounds.lower()).isEqualTo(created);
        assertThat(bounds.upper()).isBetween(before, after);
        assertThat(result.resumeTimestamp()).isNull();
    }

    @Test
    void getMessagesDefaultUnknownChannel() {
        // given
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(null);

        // when
        final var result = service.getMessages(
                ClprMessagesRequest.builder().channelId(CHANNEL_ID).build());

        // then
        assertThat(result.messages()).isEmpty();
        assertThat(result.resumeTimestamp()).isNull();
        verifyNoInteractions(messageRepository);
    }

    @Test
    void getMessagesExplicitTimestampHasNoResumeAndSkipsChannelLookup() {
        // given
        final var request = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.GTE, 100L), param(RangeOperator.LTE, 200L)))
                .build();

        // when
        final var result = service.getMessages(request);

        // then
        assertThat(result.resumeTimestamp()).isNull();
        verifyNoInteractions(channelRepository);
    }

    @ParameterizedTest
    @MethodSource("boundedRanges")
    void getMessagesAcceptsRangeOfExactlyMaximum(
            RangeOperator lowerOperator, RangeOperator upperOperator, boolean withConnector) {
        // given
        final long lower = inclusiveLower(lowerOperator);
        final long upper = lower + properties.getMaxClprMessagesTimestampRange().toNanos();
        final var request = messagesRequest(lowerOperator, upperOperator, upper, withConnector);

        // when
        service.getMessages(request);

        // then
        verifyMessagesQuery(lower, upper, withConnector);
    }

    @ParameterizedTest
    @MethodSource("boundedRanges")
    void getMessagesTruncatesDescendingRangeOneNanosecondOverMaximum(
            RangeOperator lowerOperator, RangeOperator upperOperator, boolean withConnector) {
        // given
        final long maxRange = properties.getMaxClprMessagesTimestampRange().toNanos();
        final long upper = inclusiveLower(lowerOperator) + maxRange + 1;
        final var request = messagesRequest(lowerOperator, upperOperator, upper, withConnector);

        // when
        final var result = service.getMessages(request);

        // then the newest part is kept and the oldest nanosecond is left to the next link
        assertThat(result.resumeTimestamp()).isEqualTo(upper - maxRange);
        verifyMessagesQuery(upper - maxRange, upper, withConnector);
    }

    @ParameterizedTest
    @MethodSource("boundedRanges")
    void getMessagesTruncatesAscendingRangeOneNanosecondOverMaximum(
            RangeOperator lowerOperator, RangeOperator upperOperator, boolean withConnector) {
        // given
        final long maxRange = properties.getMaxClprMessagesTimestampRange().toNanos();
        final long lower = inclusiveLower(lowerOperator);
        final long upper = lower + maxRange + 1;
        final var request = messagesRequest(lowerOperator, upperOperator, upper, withConnector, Direction.ASC);

        // when
        final var result = service.getMessages(request);

        // then the oldest part is kept and the newest nanosecond is left to the next link
        assertThat(result.resumeTimestamp()).isEqualTo(lower + maxRange);
        verifyMessagesQuery(lower, lower + maxRange, withConnector);
    }

    @Test
    void getMessagesTruncatesOneSidedRanges() {
        // given
        final long maxRange = properties.getMaxClprMessagesTimestampRange().toNanos();
        final long upper = 3 * maxRange;
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(channel(ACTIVE));
        final var request = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.LTE, upper)))
                .build();

        // when
        final var result = service.getMessages(request);

        // then
        assertThat(result.resumeTimestamp()).isEqualTo(upper - maxRange);
        verifyMessagesQuery(upper - maxRange, upper, false);
    }

    @Test
    void getMessagesUpperBoundBeforeChannelCreationIsEmpty() {
        // given
        when(channelRepository.findByChannelId(CHANNEL_ID)).thenReturn(item(CHANNEL_ID, 500L, ACTIVE));
        final var request = ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .timestamp(timestamp(param(RangeOperator.LTE, 100L)))
                .build();

        // when
        final var result = service.getMessages(request);

        // then
        assertThat(result.messages()).isEmpty();
        assertThat(result.resumeTimestamp()).isNull();
        verifyNoInteractions(messageRepository);
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

    private static Stream<Arguments> boundedRanges() {
        final var lowerOperators = List.of(RangeOperator.GT, RangeOperator.GTE);
        final var upperOperators = List.of(RangeOperator.LT, RangeOperator.LTE);
        return lowerOperators.stream()
                .flatMap(lower -> upperOperators.stream()
                        .flatMap(upper ->
                                Stream.of(true, false).map(connector -> Arguments.of(lower, upper, connector))));
    }

    /** The inclusive lower bound the service computes for a lower operator and a raw value of {@code LOWER}. */
    private static long inclusiveLower(RangeOperator lowerOperator) {
        return lowerOperator == RangeOperator.GT ? LOWER + 1 : LOWER;
    }

    /** Builds a request whose adjusted inclusive upper bound is {@code upper}, whatever the upper operator. */
    private static ClprMessagesRequest messagesRequest(
            RangeOperator lowerOperator, RangeOperator upperOperator, long upper, boolean withConnector) {
        return messagesRequest(lowerOperator, upperOperator, upper, withConnector, Direction.DESC);
    }

    private static ClprMessagesRequest messagesRequest(
            RangeOperator lowerOperator,
            RangeOperator upperOperator,
            long upper,
            boolean withConnector,
            Direction order) {
        final long rawUpper = upperOperator == RangeOperator.LT ? upper + 1 : upper;
        return ClprMessagesRequest.builder()
                .channelId(CHANNEL_ID)
                .connectorId(withConnector ? CONNECTOR_ID : null)
                .order(order)
                .timestamp(timestamp(param(lowerOperator, LOWER), param(upperOperator, rawUpper)))
                .build();
    }

    private void verifyMessagesQuery(long lower, long upper, boolean withConnector) {
        if (withConnector) {
            verify(messageRepository)
                    .findByChannelIdAndConnectorIdAndTimestampBetween(
                            eq(CHANNEL_ID), eq(CONNECTOR_ID), eq(lower), eq(upper), isNull(), any());
            return;
        }

        verify(messageRepository)
                .findByChannelIdAndTimestampBetween(eq(CHANNEL_ID), eq(lower), eq(upper), isNull(), any());
    }

    private static ClprMessageDto message(long consensusTimestamp, int type) {
        return ClprMessageDto.builder()
                .channelId(CHANNEL_ID)
                .consensusTimestamp(consensusTimestamp)
                .messageId(1L)
                .messageType(type)
                .build();
    }

    private MessageBounds captureMessageBounds() {
        final var lower = ArgumentCaptor.forClass(Long.class);
        final var upper = ArgumentCaptor.forClass(Long.class);
        verify(messageRepository)
                .findByChannelIdAndTimestampBetween(eq(CHANNEL_ID), lower.capture(), upper.capture(), isNull(), any());
        return new MessageBounds(lower.getValue(), upper.getValue());
    }

    private static ClprChannelDto channel(int status) {
        return item(CHANNEL_ID, 0L, status);
    }

    private static TimestampParameter param(RangeOperator operator, long value) {
        return new TimestampParameter(operator, value);
    }

    private static Bound timestamp(TimestampParameter... params) {
        return Bound.of(params, "timestamp", null);
    }

    private static ClprChannelDto item(byte @Nullable [] channelId, long created, int status) {
        return item(channelId, created, status, new byte[] {1});
    }

    private static ClprChannelDto item(
            byte @Nullable [] channelId, long created, int status, byte[] ownershipCommitment) {
        return ClprChannelDto.builder()
                .channelId(channelId)
                .createdTimestamp(created)
                .ownershipCommitment(ownershipCommitment)
                .status(status)
                .build();
    }

    private record MessageBounds(long lower, long upper) {}
}
