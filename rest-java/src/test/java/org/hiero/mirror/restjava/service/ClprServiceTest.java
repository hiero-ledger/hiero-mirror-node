// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.google.common.collect.Range;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.dto.ClprConnectorsRequest;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.hiero.mirror.restjava.dto.ClprMessagesRequest;
import org.hiero.mirror.restjava.dto.ClprPendingRequest;
import org.jspecify.annotations.Nullable;
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
        final var maxRange = queryProperties.getMaxClprTimestampRange().toNanos();
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
        final var maxRange = queryProperties.getMaxClprTimestampRange().toNanos();
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
    void getChannelsByChannelIdAsOfTimestamp() {
        // given
        final var current = domainBuilder
                .clprChannel()
                .customize(c -> c.status(ClprChannelStatus.CLOSED).timestampRange(Range.atLeast(200L)))
                .persist();
        final var history = domainBuilder
                .clprChannelHistory()
                .customize(c -> c.channelId(current.getChannelId())
                        .status(ClprChannelStatus.ACTIVE)
                        .timestampRange(Range.closedOpen(100L, 200L)))
                .persist();

        // when / then
        assertThat(service.getChannels(asOf(current, 100L, null)))
                .extracting(ClprChannel::getStatus)
                .containsExactly(history.getStatus());
        assertThat(service.getChannels(asOf(current, 199L, null)))
                .extracting(ClprChannel::getStatus)
                .containsExactly(ClprChannelStatus.ACTIVE);
        assertThat(service.getChannels(asOf(current, 200L, null))).containsExactly(current);
        assertThat(service.getChannels(asOf(current, 99L, null))).isEmpty();
        assertThat(service.getChannels(asOf(current, 150L, ClprChannelStatus.CLOSED)))
                .isEmpty();
    }

    @Test
    void getChannelsByChannelIdWithTimestampRequiresEq() {
        // given
        final var request = ClprChannelsRequest.builder()
                .channelId(new byte[32])
                .lowerBound(1L)
                .upperBound(99L)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timestamp must use the eq operator when filtering by channel.id");
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
        final var maxRange = queryProperties.getMaxClprTimestampRange().toNanos();
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
        final var maxRange = queryProperties.getMaxClprTimestampRange().toNanos();
        final var request = ClprChannelsRequest.builder()
                .lowerBound(100L)
                .upperBound(100L + maxRange + 1)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getChannels(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed");
    }

    @Test
    void getPendingChannels() {
        // given
        final var open1 = persistPending(100L, false, null);
        persistPending(150L, false, 160L); // completed
        persistPending(175L, true, null); // deleted
        final var open2 = persistPending(200L, false, null);
        final var request = ClprPendingRequest.builder().build();
        final var bounded = ClprPendingRequest.builder().lowerBound(150L).build();

        // when / then
        assertThat(service.getPendingChannels(request)).containsExactly(open2, open1);
        assertThat(service.getPendingChannels(bounded)).containsExactly(open2);
    }

    @Test
    void getPendingChannelsInvalidTimestampRange() {
        // given
        final var request =
                ClprPendingRequest.builder().lowerBound(10L).upperBound(1L).build();

        // when / then
        assertThatThrownBy(() -> service.getPendingChannels(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid range provided for timestamp");
    }

    @Test
    void getPendingConnectors() {
        // given
        final var open1 = persistPendingConnector(100L, false, null);
        persistPendingConnector(150L, false, 160L); // completed
        persistPendingConnector(175L, true, null); // deleted
        final var open2 = persistPendingConnector(200L, false, null);
        final var request = ClprPendingRequest.builder().build();
        final var bounded = ClprPendingRequest.builder().lowerBound(150L).build();

        // when / then
        assertThat(service.getPendingConnectors(request)).containsExactly(open2, open1);
        assertThat(service.getPendingConnectors(bounded)).containsExactly(open2);
    }

    @Test
    void getPendingConnectorsInvalidTimestampRange() {
        // given
        final var request =
                ClprPendingRequest.builder().lowerBound(10L).upperBound(1L).build();

        // when / then
        assertThatThrownBy(() -> service.getPendingConnectors(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid range provided for timestamp");
    }

    @Test
    void getConnectors() {
        // given
        final var connector1 = persistConnector(100L);
        final var connector2 = persistConnector(200L);
        final var request = ClprConnectorsRequest.builder().build();

        // when / then
        assertThat(service.getConnectors(request)).containsExactly(connector2, connector1);
    }

    @Test
    void getConnectorsByFilters() {
        // given
        final var connector = persistConnector(200L);
        persistConnector(200L); // other channel and connector
        final var request = ClprConnectorsRequest.builder()
                .channelId(connector.getChannelId())
                .connectorId(connector.getConnectorId())
                .deleted(false)
                .build();

        // when / then
        assertThat(service.getConnectors(request)).containsExactly(connector);
    }

    @Test
    void getConnectorsInvalidTimestampRange() {
        // given
        final var request = ClprConnectorsRequest.builder()
                .lowerBound(300L)
                .upperBound(200L)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getConnectors(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid range provided for timestamp");
    }

    @Test
    void getConnectorsConnectorIdWithoutChannelId() {
        // given
        final var request =
                ClprConnectorsRequest.builder().connectorId(new byte[32]).build();

        // when / then
        assertThatThrownBy(() -> service.getConnectors(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channel.id is required when filtering by connector.id, deleted or timestamp");
    }

    @Test
    void getConnectorsByChannelIdWithDeletedAndTimestamp() {
        // given
        final var connector = persistConnector(200L);
        final var request = ClprConnectorsRequest.builder()
                .channelId(connector.getChannelId())
                .deleted(false)
                .lowerBound(1L)
                .upperBound(1000L)
                .build();

        // when / then
        assertThat(service.getConnectors(request)).containsExactly(connector);
    }

    @Test
    void getConnectorsTimestampRangeExceedsMax() {
        // given
        final var maxRange = queryProperties.getMaxClprTimestampRange().toNanos();
        final var request = ClprConnectorsRequest.builder()
                .channelId(new byte[32])
                .lowerBound(1L)
                .upperBound(maxRange + 2)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getConnectors(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed");
    }

    @Test
    void getConnectorsByChannelIdAndConnectorIdAsOfTimestamp() {
        // given
        final var current = domainBuilder
                .clprConnector()
                .customize(c -> c.slashCount(2L).timestampRange(Range.atLeast(200L)))
                .persist();
        domainBuilder
                .clprConnectorHistory()
                .customize(c -> c.channelId(current.getChannelId())
                        .connectorId(current.getConnectorId())
                        .slashCount(1L)
                        .timestampRange(Range.closedOpen(100L, 200L)))
                .persist();

        // when / then
        assertThat(service.getConnectors(asOf(current, 150L)))
                .extracting(ClprConnector::getSlashCount)
                .containsExactly(1L);
        assertThat(service.getConnectors(asOf(current, 200L))).containsExactly(current);
        assertThat(service.getConnectors(asOf(current, 99L))).isEmpty();
    }

    @Test
    void getConnectorsAsOfTimestampRequiresEq() {
        // given
        final var request = ClprConnectorsRequest.builder()
                .channelId(new byte[32])
                .connectorId(new byte[32])
                .lowerBound(1L)
                .upperBound(99L)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getConnectors(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timestamp must use the eq operator when filtering by channel.id and connector.id");
    }

    @Test
    void getConnectorsDeletedWithoutChannelId() {
        // given
        final var request = ClprConnectorsRequest.builder().deleted(true).build();

        // when / then
        assertThatThrownBy(() -> service.getConnectors(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channel.id is required when filtering by connector.id, deleted or timestamp");
    }

    @Test
    void getConnectorsDeletedWithConnectorIdWithoutChannelId() {
        // given
        final var request = ClprConnectorsRequest.builder()
                .connectorId(new byte[32])
                .deleted(false)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getConnectors(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channel.id is required when filtering by connector.id, deleted or timestamp");
    }

    @Test
    void getConnectorsTimestampWithoutChannelId() {
        // given
        final var lowerOnly = ClprConnectorsRequest.builder().lowerBound(1L).build();
        final var upperOnly = ClprConnectorsRequest.builder().upperBound(100L).build();
        final var both =
                ClprConnectorsRequest.builder().lowerBound(1L).upperBound(100L).build();

        // when / then
        for (final var request : List.of(lowerOnly, upperOnly, both)) {
            assertThatThrownBy(() -> service.getConnectors(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("channel.id is required when filtering by connector.id, deleted or timestamp");
        }
    }

    @Test
    void getMessages() {
        // given
        final var channel = persistChannelWithAcked(1L);
        final var message1 = persistMessage(channel.getChannelId(), 100L, 1L);
        final var message2 = persistMessage(channel.getChannelId(), 200L, 2L);
        persistMessage(domainBuilder.bytes(32), 150L, 3L); // other channel
        final var request =
                ClprMessagesRequest.builder().channelId(channel.getChannelId()).build();

        // when
        final var actual = service.getMessages(request);

        // then message 1 is at or below the channel's acked_message_id and message 2 isn't
        assertThat(actual)
                .extracting(ClprMessageDto::messageId, ClprMessageDto::acknowledged)
                .containsExactly(tuple(message2.getMessageId(), false), tuple(message1.getMessageId(), true));
    }

    @Test
    void getMessagesUnknownChannel() {
        // given
        persistMessage(domainBuilder.bytes(32), 100L, 1L);
        final var request =
                ClprMessagesRequest.builder().channelId(new byte[32]).build();

        // when / then
        assertThat(service.getMessages(request)).isEmpty();
    }

    @Test
    void getMessagesByConnectorIdDoesNotRequireTimestamp() {
        // given
        final var channel = persistChannelWithAcked(0L);
        final var connectorId = domainBuilder.bytes(32);
        final var message = persistMessage(channel.getChannelId(), connectorId, 100L, 1L, ClprMessageType.DATA);
        persistMessage(channel.getChannelId(), 200L, 2L); // other connector
        final var request = ClprMessagesRequest.builder()
                .channelId(channel.getChannelId())
                .connectorId(connectorId)
                .build();

        // when / then
        assertThat(ids(service.getMessages(request))).containsExactly(message.getMessageId());
    }

    @Test
    void getMessagesMessageIdWithoutTimestamp() {
        // given
        final var channel = persistChannelWithAcked(0L);
        final var request = ClprMessagesRequest.builder()
                .channelId(channel.getChannelId())
                .messageIdLowerBound(2L)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getMessages(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timestamp is required when filtering by message.id");
    }

    @Test
    void getMessagesTypeWithoutTimestamp() {
        // given
        final var channel = persistChannelWithAcked(0L);
        final var request = ClprMessagesRequest.builder()
                .channelId(channel.getChannelId())
                .type(ClprMessageType.CONTROL)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getMessages(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timestamp is required when filtering by type");
    }

    @Test
    void getMessagesMessageIdAndTypeWithTimestamp() {
        // given
        final var channel = persistChannelWithAcked(0L);
        persistMessage(channel.getChannelId(), null, 100L, 1L, ClprMessageType.CONTROL);
        final var expected = persistMessage(channel.getChannelId(), null, 100L, 2L, ClprMessageType.CONTROL);
        persistMessage(channel.getChannelId(), 100L, 3L); // DATA
        final var request = ClprMessagesRequest.builder()
                .channelId(channel.getChannelId())
                .messageIdLowerBound(2L)
                .timestampLowerBound(100L)
                .timestampUpperBound(100L)
                .type(ClprMessageType.CONTROL)
                .build();

        // when / then
        assertThat(ids(service.getMessages(request))).containsExactly(expected.getMessageId());
    }

    @Test
    void getMessagesTimestampRangeExceedsMax() {
        // given
        final var channel = persistChannelWithAcked(0L);
        final var maxRange = queryProperties.getMaxClprTimestampRange().toNanos();
        final var request = ClprMessagesRequest.builder()
                .channelId(channel.getChannelId())
                .timestampLowerBound(1L)
                .timestampUpperBound(maxRange + 2)
                .build();

        // when / then
        assertThatThrownBy(() -> service.getMessages(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed");
    }

    @Test
    void getMessagesTimestampRangeAtMax() {
        // given
        final var channel = persistChannelWithAcked(0L);
        final var maxRange = queryProperties.getMaxClprTimestampRange().toNanos();
        final var message = persistMessage(channel.getChannelId(), maxRange, 1L);
        final var request = ClprMessagesRequest.builder()
                .channelId(channel.getChannelId())
                .timestampLowerBound(1L)
                .timestampUpperBound(maxRange + 1)
                .build();

        // when / then
        assertThat(ids(service.getMessages(request))).containsExactly(message.getMessageId());
    }

    @Test
    void getMessagesOpenEndedTimestampIsNotCapped() {
        // given
        final var channel = persistChannelWithAcked(0L);
        final var message = persistMessage(channel.getChannelId(), domainBuilder.timestamp(), 1L);
        final var request = ClprMessagesRequest.builder()
                .channelId(channel.getChannelId())
                .timestampLowerBound(1L)
                .build();

        // when / then
        assertThat(ids(service.getMessages(request))).containsExactly(message.getMessageId());
    }

    private static List<Long> ids(List<ClprMessageDto> messages) {
        return messages.stream().map(ClprMessageDto::messageId).toList();
    }

    private ClprChannel persistChannelWithAcked(long ackedMessageId) {
        return domainBuilder
                .clprChannel()
                .customize(c -> c.ackedMessageId(ackedMessageId))
                .persist();
    }

    private ClprMessage persistMessage(byte[] channelId, long consensusTimestamp, long messageId) {
        return persistMessage(channelId, domainBuilder.bytes(32), consensusTimestamp, messageId, ClprMessageType.DATA);
    }

    private ClprMessage persistMessage(
            byte[] channelId,
            byte @Nullable [] connectorId,
            long consensusTimestamp,
            long messageId,
            ClprMessageType type) {
        return domainBuilder
                .clprMessage()
                .customize(m -> m.channelId(channelId)
                        .connectorId(connectorId)
                        .consensusTimestamp(consensusTimestamp)
                        .messageId(messageId)
                        .messageType(type))
                .persist();
    }

    private ClprConnector persistConnector(long createdTimestamp) {
        return domainBuilder
                .clprConnector()
                .customize(c -> c.createdTimestamp(createdTimestamp))
                .persist();
    }

    private ClprConnectorsRequest asOf(ClprConnector connector, long timestamp) {
        return ClprConnectorsRequest.builder()
                .channelId(connector.getChannelId())
                .connectorId(connector.getConnectorId())
                .lowerBound(timestamp)
                .upperBound(timestamp)
                .build();
    }

    private ClprChannelsRequest asOf(ClprChannel channel, long timestamp, @Nullable ClprChannelStatus status) {
        final var builder = ClprChannelsRequest.builder()
                .channelId(channel.getChannelId())
                .lowerBound(timestamp)
                .upperBound(timestamp);
        if (status != null) {
            builder.status(status);
        }
        return builder.build();
    }

    private ClprChannelPendingCommitment persistPending(
            long createdTimestamp, boolean deleted, @Nullable Long completedTimestamp) {
        return domainBuilder
                .clprChannelPendingCommitment()
                .customize(c -> c.completedTimestamp(completedTimestamp)
                        .createdTimestamp(createdTimestamp)
                        .deleted(deleted))
                .persist();
    }

    private ClprConnectorPendingCommitment persistPendingConnector(
            long createdTimestamp, boolean deleted, @Nullable Long completedTimestamp) {
        return domainBuilder
                .clprConnectorPendingCommitment()
                .customize(c -> c.completedTimestamp(completedTimestamp)
                        .createdTimestamp(createdTimestamp)
                        .deleted(deleted))
                .persist();
    }

    private ClprChannel persist(long createdTimestamp, ClprChannelStatus status) {
        return domainBuilder
                .clprChannel()
                .customize(c -> c.createdTimestamp(createdTimestamp).status(status))
                .persist();
    }
}
