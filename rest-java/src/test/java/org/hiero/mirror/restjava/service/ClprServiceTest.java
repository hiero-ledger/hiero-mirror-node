// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static com.hederahashgraph.api.proto.java.ClprChannelStatus.ACTIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.CLOSED_VALUE;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.PAUSED_VALUE;
import static com.hederahashgraph.api.proto.java.ClprChannelStatus.PENDING_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort.Direction;

final class ClprServiceTest {

    private static final byte[] CHANNEL_ID = new byte[] {1, 2, 3};

    private ClprChannelRepository channelRepository;
    private ClprService service;

    @BeforeEach
    void setup() {
        channelRepository = mock(ClprChannelRepository.class);
        service = new ClprServiceImpl(channelRepository);
    }

    @Test
    void getChannelsById() {
        // given: without a timestamp the bounds are open, so this is the current state
        when(channelRepository.findByChannelIdAndTimestampBetween(CHANNEL_ID, 0L, Long.MAX_VALUE, null))
                .thenReturn(List.of(channel(ACTIVE_VALUE)));
        final var request = ClprChannelsRequest.builder().channelId(CHANNEL_ID).build();

        // when / then
        assertThat(service.getChannels(request)).singleElement().satisfies(c -> {
            assertThat(c.channelId()).isEqualTo(CHANNEL_ID);
            assertThat(c.status()).isEqualTo(ACTIVE_VALUE);
        });
    }

    @Test
    void getChannelsByIdWithStatus() {
        // given
        final var request = ClprChannelsRequest.builder()
                .channelId(CHANNEL_ID)
                .status(ClprChannelStatus.ACTIVE)
                .build();

        // when
        service.getChannels(request);

        // then
        verify(channelRepository).findByChannelIdAndTimestampBetween(CHANNEL_ID, 0L, Long.MAX_VALUE, ACTIVE_VALUE);
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
                .satisfies(c -> assertThat(c.status()).isEqualTo(PAUSED_VALUE));
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
        final var items = List.of(
                item(CHANNEL_ID, 3_000L, ACTIVE_VALUE),
                item(null, 2_000L, PENDING_VALUE),
                item(new byte[] {9, 9}, 1_000L, CLOSED_VALUE));
        when(channelRepository.findAllByCreatedTimestampBetween(0L, Long.MAX_VALUE, null, Direction.DESC, 25))
                .thenReturn(items);

        // when / then
        assertThat(service.getChannels(ClprChannelsRequest.builder().build()))
                .extracting(ClprChannelDto::status)
                .containsExactly(ACTIVE_VALUE, PENDING_VALUE, CLOSED_VALUE);
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
        verify(channelRepository).findAllByCreatedTimestampBetween(101L, 199L, CLOSED_VALUE, Direction.ASC, 10);
    }

    @Test
    void getChannelsListPending() {
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
        verify(channelRepository).findAllByCreatedTimestampBetween(100L, 200L, PENDING_VALUE, Direction.ASC, 5);
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
}
