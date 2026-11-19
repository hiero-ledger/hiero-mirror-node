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

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.dto.ClprChannelDto;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.mapper.ClprChannelMapperImpl;
import org.hiero.mirror.restjava.mapper.ClprEndpointManifestMapperImpl;
import org.hiero.mirror.restjava.mapper.ClprLedgerConfigurationMapperImpl;
import org.hiero.mirror.restjava.mapper.CommonMapperImpl;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.repository.ClprChannelRepository;
import org.hiero.mirror.restjava.repository.ClprEndpointManifestRepository;
import org.hiero.mirror.restjava.repository.ClprLedgerConfigurationRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

final class ClprServiceTest {

    private static final byte[] CHANNEL_ID = new byte[] {1, 2, 3};
    private static final String CREATED_TIMESTAMP = "createdTimestamp";

    private ClprChannelRepository channelRepository;
    private ClprEndpointManifestRepository endpointManifestRepository;
    private ClprLedgerConfigurationRepository ledgerConfigurationRepository;
    private ClprService service;

    @BeforeEach
    void setup() {
        channelRepository = mock(ClprChannelRepository.class);
        endpointManifestRepository = mock(ClprEndpointManifestRepository.class);
        ledgerConfigurationRepository = mock(ClprLedgerConfigurationRepository.class);
        final var commonMapper = new CommonMapperImpl();
        final var channelMapper = new ClprChannelMapperImpl(commonMapper);
        service = new ClprServiceImpl(
                channelMapper,
                channelRepository,
                new ClprEndpointManifestMapperImpl(channelMapper, commonMapper),
                endpointManifestRepository,
                new ClprLedgerConfigurationMapperImpl(commonMapper),
                ledgerConfigurationRepository);
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
    void getEndpointManifestWithoutTimestamp() {
        // given
        when(endpointManifestRepository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(
                        0L, Long.MAX_VALUE))
                .thenReturn(ClprEndpointManifest.builder()
                        .consensusTimestamp(1_000_000_000L)
                        .endpoints(List.of())
                        .build());

        // when / then
        assertThat(service.getEndpointManifest(timestamp()).getConsensusTimestamp())
                .isEqualTo("1.000000000");
    }

    @ParameterizedTest
    @MethodSource("timestampBounds")
    void getEndpointManifestTimestampBounds(TimestampParameter[] params, long lower, long upper) {
        // given
        when(endpointManifestRepository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(lower, upper))
                .thenReturn(ClprEndpointManifest.builder()
                        .consensusTimestamp(upper)
                        .endpoints(List.of())
                        .build());

        // when
        service.getEndpointManifest(timestamp(params));

        // then
        verify(endpointManifestRepository)
                .findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(lower, upper);
    }

    @Test
    void getEndpointManifestNotFound() {
        assertThatThrownBy(() -> service.getEndpointManifest(timestamp(param(RangeOperator.LTE, 100L))))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("CLPR endpoint manifest not found");
    }

    @ParameterizedTest
    @MethodSource("invalidTimestamps")
    void getEndpointManifestRejectsInvalidTimestamp(TimestampParameter[] params) {
        assertThatThrownBy(() -> service.getEndpointManifest(timestamp(params)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(endpointManifestRepository);
    }

    @Test
    void getLedgerConfigurationWithoutTimestamp() {
        // given
        when(ledgerConfigurationRepository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(
                        0L, Long.MAX_VALUE))
                .thenReturn(ClprLedgerConfiguration.builder()
                        .consensusTimestamp(1_000_000_000L)
                        .build());

        // when / then
        assertThat(service.getLedgerConfiguration(timestamp()).getConsensusTimestamp())
                .isEqualTo("1.000000000");
    }

    @ParameterizedTest
    @MethodSource("timestampBounds")
    void getLedgerConfigurationTimestampBounds(TimestampParameter[] params, long lower, long upper) {
        // given
        when(ledgerConfigurationRepository.findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(
                        lower, upper))
                .thenReturn(ClprLedgerConfiguration.builder()
                        .consensusTimestamp(upper)
                        .build());

        // when
        service.getLedgerConfiguration(timestamp(params));

        // then
        verify(ledgerConfigurationRepository)
                .findFirstByConsensusTimestampBetweenOrderByConsensusTimestampDesc(lower, upper);
    }

    @Test
    void getLedgerConfigurationNotFound() {
        assertThatThrownBy(() -> service.getLedgerConfiguration(timestamp(param(RangeOperator.LTE, 100L))))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("CLPR ledger configuration not found");
    }

    @ParameterizedTest
    @MethodSource("invalidTimestamps")
    void getLedgerConfigurationRejectsInvalidTimestamp(TimestampParameter[] params) {
        assertThatThrownBy(() -> service.getLedgerConfiguration(timestamp(params)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ledgerConfigurationRepository);
    }

    private static Stream<Arguments> invalidTimestamps() {
        return Stream.of(
                Arguments.of((Object) new TimestampParameter[] {param(RangeOperator.NE, 100L)}),
                Arguments.of((Object)
                        new TimestampParameter[] {param(RangeOperator.GT, 100L), param(RangeOperator.GTE, 200L)}),
                Arguments.of((Object)
                        new TimestampParameter[] {param(RangeOperator.GTE, 200L), param(RangeOperator.LTE, 100L)}),
                Arguments.of((Object)
                        new TimestampParameter[] {param(RangeOperator.GT, 200L), param(RangeOperator.LT, 200L)}));
    }

    /** eq is treated as an upper bound, and gt/lt are adjusted to inclusive bounds. */
    private static Stream<Arguments> timestampBounds() {
        return Stream.of(
                Arguments.of(new TimestampParameter[] {param(RangeOperator.EQ, 150L)}, 0L, 150L),
                Arguments.of(new TimestampParameter[] {param(RangeOperator.LTE, 150L)}, 0L, 150L),
                Arguments.of(new TimestampParameter[] {param(RangeOperator.LT, 150L)}, 0L, 149L),
                Arguments.of(new TimestampParameter[] {param(RangeOperator.GTE, 150L)}, 150L, Long.MAX_VALUE),
                Arguments.of(new TimestampParameter[] {param(RangeOperator.GT, 150L)}, 151L, Long.MAX_VALUE),
                Arguments.of(
                        new TimestampParameter[] {param(RangeOperator.GTE, 100L), param(RangeOperator.LT, 200L)},
                        100L,
                        199L));
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
