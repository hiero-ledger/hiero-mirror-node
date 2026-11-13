// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.restjava.dto.ClprMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ClprMessageMapperTest {

    private ClprMessageMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ClprMessageMapperImpl(new CommonMapperImpl());
    }

    @Test
    void mapData() {
        // given
        final var message = ClprMessageDto.builder()
                .acknowledged(true)
                .channelId(new byte[] {0x01, 0x02})
                .connectorId(new byte[] {0x03})
                .consensusTimestamp(1_000_000_001L)
                .messageData(new byte[] {(byte) 0xde, (byte) 0xad})
                .messageId(5L)
                .messageType("DATA")
                .runningHash(new byte[] {(byte) 0xaa})
                .sender(new byte[] {0x04})
                .targetApplication(new byte[] {0x05})
                .build();

        // when
        final var actual = mapper.map(message);

        // then
        assertThat(actual.getAcknowledged()).isTrue();
        assertThat(actual.getChannelId()).isEqualTo("0x0102");
        assertThat(actual.getConnectorId()).isEqualTo("0x03");
        assertThat(actual.getMessageData()).isEqualTo("0xdead");
        assertThat(actual.getMessageId()).isEqualTo(5L);
        assertThat(actual.getReplyStatus()).isNull();
        assertThat(actual.getReplyToMessageId()).isNull();
        assertThat(actual.getRunningHash()).isEqualTo("0xaa");
        assertThat(actual.getSender()).isEqualTo("0x04");
        assertThat(actual.getTargetApplication()).isEqualTo("0x05");
        assertThat(actual.getTimestamp()).isEqualTo("1.000000001");
        assertThat(actual.getType().getValue()).isEqualTo("DATA");
    }

    @Test
    void mapResponse() {
        // given
        final var message = ClprMessageDto.builder()
                .channelId(new byte[] {0x01})
                .consensusTimestamp(2_000_000_000L)
                .messageId(6L)
                .messageType("RESPONSE")
                .replyStatus("SUCCESS")
                .replyToMessageId(5L)
                .build();

        // when
        final var actual = mapper.map(message);

        // then
        assertThat(actual.getAcknowledged()).isFalse();
        assertThat(actual.getConnectorId()).isNull();
        assertThat(actual.getReplyStatus().getValue()).isEqualTo("SUCCESS");
        assertThat(actual.getReplyToMessageId()).isEqualTo(5L);
        assertThat(actual.getSender()).isNull();
        assertThat(actual.getTargetApplication()).isNull();
        assertThat(actual.getType().getValue()).isEqualTo("RESPONSE");
    }
}
