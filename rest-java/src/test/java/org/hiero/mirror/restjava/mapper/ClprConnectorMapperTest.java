// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.Key.TypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ClprConnectorMapperTest {

    private ClprConnectorMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ClprConnectorMapperImpl(new CommonMapperImpl());
    }

    @Test
    void map() {
        // given
        final var connector = ClprConnector.builder()
                .adminKey(com.hederahashgraph.api.proto.java.Key.newBuilder()
                        .setEd25519(com.google.protobuf.ByteString.copyFrom(new byte[32]))
                        .build()
                        .toByteArray())
                .channelId(new byte[] {0x01, 0x02, 0x03})
                .commitment(new byte[] {(byte) 0x9c, 0x2e})
                .connectorContractId(EntityId.of(789L))
                .connectorId(new byte[] {0x4d, 0x5e})
                .createdTimestamp(1_000_000_000L)
                .inFlightMessageCount(2L)
                .lockedStake(100_000_000L)
                .publicKey(new byte[] {0x04, (byte) 0xa1})
                .salt(new byte[] {0x01})
                .signature(new byte[] {(byte) 0x9f, (byte) 0x8e})
                .signatureScheme(ClprSignatureScheme.ECDSA_SECP256K1)
                .slashCount(1L)
                .stakeRecipientId(EntityId.of(1001L))
                .status(ClprConnectorStatus.DELETED)
                .timestampRange(Range.closedOpen(2_000_000_000L, 3_000_000_000L))
                .build();

        // when
        final var actual = mapper.map(connector);

        // then
        assertThat(actual.getAdminKey())
                .isNotNull()
                .satisfies(key -> assertThat(key.getType()).isEqualTo(TypeEnum.ED25519));
        assertThat(actual.getChannelId()).isEqualTo("0x010203");
        assertThat(actual.getCommitment()).isEqualTo("0x9c2e");
        assertThat(actual.getConnectorContractId()).isEqualTo("0.0.789");
        assertThat(actual.getConnectorId()).isEqualTo("0x4d5e");
        assertThat(actual.getCreatedTimestamp()).isEqualTo("1.000000000");
        assertThat(actual.getInFlightMessageCount()).isEqualTo(2L);
        assertThat(actual.getLockedStake()).isEqualTo("100000000");
        assertThat(actual.getPublicKey()).isEqualTo("0x04a1");
        assertThat(actual.getSalt()).isEqualTo("0x01");
        assertThat(actual.getSignature()).isEqualTo("0x9f8e");
        assertThat(actual.getSignatureScheme())
                .isEqualTo(org.hiero.mirror.rest.model.ClprSignatureScheme.ECDSA_SECP256_K1);
        assertThat(actual.getSlashCount()).isEqualTo(1L);
        assertThat(actual.getStakeRecipientId()).isEqualTo("0.0.1001");
        assertThat(actual.getStatus()).isEqualTo(org.hiero.mirror.rest.model.ClprConnectorStatus.DELETED);
        assertThat(actual.getTimestamp()).satisfies(range -> {
            assertThat(range.getFrom()).isEqualTo("2.000000000");
            assertThat(range.getTo()).isEqualTo("3.000000000");
        });
    }

    @Test
    void mapPending() {
        // given
        final var connector = ClprConnector.builder()
                .channelId(new byte[] {0x01})
                .commitment(new byte[] {0x02})
                .createdTimestamp(1_000_000_000L)
                .status(ClprConnectorStatus.PENDING)
                .timestampRange(Range.atLeast(1_000_000_000L))
                .build();

        // when
        final var actual = mapper.map(connector);

        // then
        assertThat(actual.getConnectorId()).isNull();
        assertThat(actual.getConnectorContractId()).isNull();
        assertThat(actual.getAdminKey()).isNull();
        assertThat(actual.getLockedStake()).isNull();
        assertThat(actual.getStatus()).isEqualTo(org.hiero.mirror.rest.model.ClprConnectorStatus.PENDING);
        assertThat(actual.getTimestamp().getTo()).isNull();
    }
}
