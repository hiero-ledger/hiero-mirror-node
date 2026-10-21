// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Sort.Direction;

@RequiredArgsConstructor
final class ClprServiceTest extends RestJavaIntegrationTest {

    private final QueryProperties queryProperties;
    private final ClprService service;

    @ParameterizedTest
    @EnumSource(Direction.class)
    void getChannelsOrder(Direction order) {
        // given
        final var channel1 = persist(100L, ClprChannelStatus.ACTIVE);
        final var channel2 = persist(200L, ClprChannelStatus.CLOSED);
        final var request = ClprChannelsRequest.builder().order(order).build();

        // when
        final var actual = service.getChannels(request);

        // then
        assertThat(actual)
                .containsExactlyElementsOf(
                        order.isAscending() ? List.of(channel1, channel2) : List.of(channel2, channel1));
    }

    @Test
    void getChannelsLimit() {
        // given
        persist(100L, ClprChannelStatus.ACTIVE);
        final var channel2 = persist(200L, ClprChannelStatus.ACTIVE);
        final var channel3 = persist(300L, ClprChannelStatus.ACTIVE);
        final var request = ClprChannelsRequest.builder().limit(2).build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(channel3, channel2);
    }

    @Test
    void getChannelsByStatus() {
        // given
        final var active = persist(100L, ClprChannelStatus.ACTIVE);
        persist(200L, ClprChannelStatus.CLOSED);
        final var request =
                ClprChannelsRequest.builder().status(ClprChannelStatus.ACTIVE).build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(active);
    }

    @Test
    void getChannelsByTimestampRange() {
        // given
        persist(100L, ClprChannelStatus.ACTIVE);
        final var channel2 = persist(200L, ClprChannelStatus.ACTIVE);
        final var channel3 = persist(300L, ClprChannelStatus.ACTIVE);
        persist(400L, ClprChannelStatus.ACTIVE);
        final var request =
                ClprChannelsRequest.builder().lowerBound(200L).upperBound(300L).build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(channel3, channel2);
    }

    @Test
    void getChannelsInvalidTimestampRange() {
        // given
        final var request =
                ClprChannelsRequest.builder().lowerBound(300L).upperBound(200L).build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid range provided for timestamp");
    }

    @Test
    void getChannelsTimestampRangeExceedsMax() {
        // given
        final var maxRange = queryProperties.getMaxClprChannelsTimestampRange().toNanos();
        final var request = ClprChannelsRequest.builder()
                .lowerBound(1L)
                .upperBound(maxRange + 2)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed");
    }

    @Test
    void getChannelsTimestampRangeAtMax() {
        // given
        final var maxRange = queryProperties.getMaxClprChannelsTimestampRange().toNanos();
        final var channel = persist(maxRange, ClprChannelStatus.ACTIVE);
        final var request = ClprChannelsRequest.builder()
                .lowerBound(1L)
                .upperBound(maxRange + 1)
                .build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(channel);
    }

    @Test
    void getChannelsOpenEndedRangeIsNotCapped() {
        // given
        final var channel = persist(Long.MAX_VALUE - 1, ClprChannelStatus.ACTIVE);
        final var request = ClprChannelsRequest.builder().lowerBound(1L).build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(channel);
    }

    @Test
    void getChannelsByChannelId() {
        // given
        final var channel = persist(100L, ClprChannelStatus.ACTIVE);
        persist(200L, ClprChannelStatus.ACTIVE);
        final var request =
                ClprChannelsRequest.builder().channelId(channel.getChannelId()).build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(channel);
    }

    @Test
    void getChannelsByChannelIdWithStatus() {
        // given
        final var channel = persist(100L, ClprChannelStatus.ACTIVE);
        final var matching = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .status(ClprChannelStatus.ACTIVE)
                .build();
        final var nonMatching = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .status(ClprChannelStatus.CLOSED)
                .build();

        // when / then
        assertThat(service.getChannels(matching)).containsExactly(channel);
        assertThat(service.getChannels(nonMatching)).isEmpty();
    }

    @Test
    void getChannelsByChannelIdWithTimestamp() {
        // given
        final var channel = persist(100L, ClprChannelStatus.ACTIVE);
        final var matching = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .upperBound(100L)
                .build();
        final var nonMatching = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .lowerBound(101L)
                .build();

        // when / then
        assertThat(service.getChannels(matching)).containsExactly(channel);
        assertThat(service.getChannels(nonMatching)).isEmpty();
    }

    @Test
    void getChannelsByChannelIdIsNotCappedByMaxRange() {
        // given
        final var maxRange = queryProperties.getMaxClprChannelsTimestampRange().toNanos();
        final var channel = persist(maxRange + 10, ClprChannelStatus.ACTIVE);
        final var request = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .lowerBound(1L)
                .upperBound(maxRange * 2)
                .build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(channel);
    }

    @Test
    void getChannelsByChannelIdNotFound() {
        // given
        persist(100L, ClprChannelStatus.ACTIVE);
        final var request =
                ClprChannelsRequest.builder().channelId(new byte[32]).build();

        // when / then
        assertThat(service.getChannels(request)).isEmpty();
    }

    @Test
    void getChannelsByChannelIdWithStatusAndTimestamp() {
        // given
        final var channel = persist(200L, ClprChannelStatus.ACTIVE);
        persist(200L, ClprChannelStatus.ACTIVE); // other channel
        final var matching = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .lowerBound(200L)
                .status(ClprChannelStatus.ACTIVE)
                .upperBound(200L)
                .build();
        final var wrongStatus = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .lowerBound(200L)
                .status(ClprChannelStatus.CLOSED)
                .upperBound(200L)
                .build();
        final var wrongTimestamp = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .lowerBound(201L)
                .status(ClprChannelStatus.ACTIVE)
                .build();

        // when / then
        assertThat(service.getChannels(matching)).containsExactly(channel);
        assertThat(service.getChannels(wrongStatus)).isEmpty();
        assertThat(service.getChannels(wrongTimestamp)).isEmpty();
    }

    @Test
    void getChannelsByStatusAndTimestamp() {
        // given
        persist(100L, ClprChannelStatus.ACTIVE);
        final var channel2 = persist(200L, ClprChannelStatus.ACTIVE);
        persist(300L, ClprChannelStatus.CLOSED);
        final var request = ClprChannelsRequest.builder()
                .lowerBound(150L)
                .status(ClprChannelStatus.ACTIVE)
                .upperBound(350L)
                .build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(channel2);
    }

    @Test
    void getChannelsByChannelIdLimit() {
        // given
        final var channel = persist(100L, ClprChannelStatus.ACTIVE);
        final var request = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .limit(1)
                .build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(channel);
    }

    @Test
    void getChannelsZeroLowerBoundIsOpenEnded() {
        // given
        final var maxRange = queryProperties.getMaxClprChannelsTimestampRange().toNanos();
        final var channel = persist(maxRange + 10, ClprChannelStatus.ACTIVE);
        final var request = ClprChannelsRequest.builder()
                .lowerBound(0L)
                .upperBound(maxRange * 2)
                .build();

        // when / then
        assertThat(service.getChannels(request)).containsExactly(channel);
    }

    @Test
    void getChannelsTimestampRangeOneNanosecondOverMax() {
        // given
        final var maxRange = queryProperties.getMaxClprChannelsTimestampRange().toNanos();
        final var request = ClprChannelsRequest.builder()
                .lowerBound(100L)
                .upperBound(100L + maxRange + 1)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed");
    }

    private ClprChannel persist(long createdTimestamp, ClprChannelStatus status) {
        return domainBuilder
                .clprChannel()
                .customize(c -> c.createdTimestamp(createdTimestamp).status(status))
                .persist();
    }
}
