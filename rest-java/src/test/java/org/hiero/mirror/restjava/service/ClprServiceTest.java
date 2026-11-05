// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.mapper.ClprChannelMapperImpl;
import org.hiero.mirror.restjava.mapper.ClprPendingChannelMapperImpl;
import org.hiero.mirror.restjava.mapper.CommonMapperImpl;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.hiero.mirror.restjava.repository.ClprPendingChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

final class ClprServiceTest {

    private static final byte[] CHANNEL_ID = new byte[] {1, 2, 3};
    private static final String CREATED_TIMESTAMP = "created_timestamp";

    private ClprChannelRepository channelRepository;
    private ClprPendingChannelRepository pendingChannelRepository;
    private final RestJavaProperties properties = new RestJavaProperties();
    private ClprService service;

    @BeforeEach
    void setup() {
        channelRepository = mock(ClprChannelRepository.class);
        pendingChannelRepository = mock(ClprPendingChannelRepository.class);
        final var channelMapper = new ClprChannelMapperImpl(new CommonMapperImpl());
        service = new ClprServiceImpl(
                channelMapper,
                channelRepository,
                new ClprPendingChannelMapperImpl(new CommonMapperImpl()),
                pendingChannelRepository,
                properties);
    }

    @Test
    void getChannelsByIdWithoutTimestamp() {
        // given
        final var channel = channel(org.hiero.mirror.common.domain.clpr.ClprChannelStatus.ACTIVE);
        when(channelRepository.findById(CHANNEL_ID)).thenReturn(Optional.of(channel));
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
        when(channelRepository.findById(CHANNEL_ID))
                .thenReturn(Optional.of(channel(org.hiero.mirror.common.domain.clpr.ClprChannelStatus.ACTIVE)));

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
        when(channelRepository.findById(CHANNEL_ID)).thenReturn(Optional.empty());

        // when / then
        assertThat(service.getChannels(
                        ClprChannelsRequest.builder().channelId(CHANNEL_ID).build()))
                .isEmpty();
    }

    @Test
    void getChannelsByIdAsOfTimestamp() {
        // given: eq becomes lte, so the as-of range is (0, 150]
        when(channelRepository.findByChannelIdAndTimestampBetween(CHANNEL_ID, 0L, 150L, null))
                .thenReturn(List.of(channel(org.hiero.mirror.common.domain.clpr.ClprChannelStatus.PAUSED)));
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
        when(channelRepository.findByCreatedTimestampBetween(0L, Long.MAX_VALUE, null, page))
                .thenReturn(List.of(channel(org.hiero.mirror.common.domain.clpr.ClprChannelStatus.CLOSED)));

        // when / then
        assertThat(service.getChannels(ClprChannelsRequest.builder().build()))
                .singleElement()
                .satisfies(c -> assertThat(c.getStatus()).isEqualTo(ClprChannelStatus.CLOSED));
        verifyNoInteractions(pendingChannelRepository);
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
        final var pending = ClprChannelPendingCommitment.builder()
                .createdTimestamp(1_000_000_000L)
                .ownershipCommitment(new byte[] {(byte) 0xab, (byte) 0xcd})
                .build();
        final var page = PageRequest.of(0, 25, Sort.by(Direction.DESC, CREATED_TIMESTAMP));
        when(pendingChannelRepository.findOpenByCreatedTimestampBetween(0L, Long.MAX_VALUE, page))
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
        verifyNoInteractions(channelRepository);
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
        verify(pendingChannelRepository)
                .findOpenByCreatedTimestampBetween(
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
        verifyNoInteractions(channelRepository, pendingChannelRepository);
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
        verifyNoInteractions(pendingChannelRepository);
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
        verifyNoInteractions(pendingChannelRepository);
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

    private static ClprChannel channel(org.hiero.mirror.common.domain.clpr.ClprChannelStatus status) {
        return ClprChannel.builder().channelId(CHANNEL_ID).status(status).build();
    }

    private static TimestampParameter param(RangeOperator operator, long value) {
        return new TimestampParameter(operator, value);
    }

    private static Bound timestamp(TimestampParameter... params) {
        return Bound.of(params, "timestamp", null);
    }
}
