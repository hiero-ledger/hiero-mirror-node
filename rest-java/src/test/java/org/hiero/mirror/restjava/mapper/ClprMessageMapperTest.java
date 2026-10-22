// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.clpr.ClprMessageReplyStatus;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class ClprMessageMapperTest {

    private DomainBuilder domainBuilder;
    private ClprMessageMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ClprMessageMapperImpl(new CommonMapperImpl());
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        // given
        final var source = dto(
                true,
                domainBuilder.bytes(32),
                new byte[] {(byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef},
                new byte[] {1},
                new byte[] {2, 3},
                "DATA",
                "SUCCESS");

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getAcknowledged()).isTrue();
        assertThat(actual.getTimestamp()).isEqualTo(DomainUtils.toTimestamp(source.consensusTimestamp()));
        // Opaque bytes of arbitrary length aren't padded to 32 bytes
        assertThat(actual.getMessageData()).isEqualTo("0xdeadbeef");
        assertThat(actual.getSender()).isEqualTo("0x01");
        assertThat(actual.getTargetApplication()).isEqualTo("0x0203");
    }

    @Test
    void mapNulls() {
        // given
        final var source = dto(false, null, null, null, null, "CONTROL", null);

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getAcknowledged()).isFalse();
        assertThat(actual.getConnectorId()).isNull();
        assertThat(actual.getMessageData()).isNull();
        assertThat(actual.getReplyStatus()).isNull();
        assertThat(actual.getReplyToMessageId()).isNull();
        assertThat(actual.getSender()).isNull();
        assertThat(actual.getTargetApplication()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ClprMessageType.class)
    void mapType(ClprMessageType type) {
        // given
        final var source = dto(false, null, null, null, null, type.name(), null);

        // when / then
        assertThat(mapper.map(source).getType().getValue()).isEqualTo(type.name());
    }

    @ParameterizedTest
    @EnumSource(ClprMessageReplyStatus.class)
    void mapReplyStatus(ClprMessageReplyStatus status) {
        // given
        final var source = dto(false, null, null, null, null, "DATA", status.name());

        // when / then
        assertThat(mapper.map(source).getReplyStatus().getValue()).isEqualTo(status.name());
    }

    private ClprMessageDto dto(
            boolean acknowledged,
            byte @Nullable [] connectorId,
            byte @Nullable [] messageData,
            byte @Nullable [] sender,
            byte @Nullable [] targetApplication,
            String messageType,
            @Nullable String replyStatus) {
        return new ClprMessageDto(
                acknowledged,
                domainBuilder.bytes(32),
                connectorId,
                domainBuilder.timestamp(),
                messageData,
                5L,
                messageType,
                replyStatus,
                null,
                domainBuilder.bytes(32),
                sender,
                targetApplication);
    }
}
