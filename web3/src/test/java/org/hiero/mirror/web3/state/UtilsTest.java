// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import java.time.Instant;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtilsTest {

    @Test
    void parseEd25519PublicKey() {
        final var domainBuilder = new DomainBuilder();
        final var rawKey = domainBuilder.key(KeyCase.ED25519);
        final var parsedKey = Utils.parseKey(rawKey);

        assertThat(parsedKey).isNotNull();
        assertThat(parsedKey.hasEd25519()).isTrue();
    }

    @Test
    void parseEcdsaSecp256k1PublicKey() {
        final var domainBuilder = new DomainBuilder();
        final var rawKey = domainBuilder.key(KeyCase.ECDSA_SECP256K1);
        final var parsedKey = Utils.parseKey(rawKey);

        assertThat(parsedKey).isNotNull();
        assertThat(parsedKey.hasEcdsaSecp256k1()).isTrue();
    }

    @Test
    void parseInvalidKey() {
        byte[] invalidKeyBytes = new byte[] {};

        Key parsedKey = Utils.parseKey(invalidKeyBytes);

        assertNull(parsedKey);
    }

    @Test
    void parseThrowsExceptionAndReturnsNull() {
        byte[] invalidKeyBytes = new byte[] {0x01, 0x02, 0x03, 0x04};

        Key parsedKey = Utils.parseKey(invalidKeyBytes);

        assertNull(parsedKey);
    }

    @Test
    void testConvertToTimestamp() {
        // Given
        Instant expectedInstant = Instant.now();

        long expectedEpochSecond = expectedInstant.getEpochSecond();
        int expectedNano = expectedInstant.getNano();
        long timestampNanos = DomainUtils.convertToNanos(expectedEpochSecond, expectedNano);

        // When
        Timestamp result = Utils.convertToTimestamp(timestampNanos);

        // Then
        assertThat(result.seconds()).isEqualTo(expectedEpochSecond);
        assertThat(result.nanos()).isEqualTo(expectedNano);
    }

    @Test
    void toFileIDMapsShardRealmNumToFileID() {
        final var entityId = EntityId.of(0L, 0L, 111L);

        final var fileId = Utils.toFileID(entityId);

        assertThat(fileId).isNotNull();
        assertThat(fileId.shardNum()).isEqualTo(0);
        assertThat(fileId.realmNum()).isEqualTo(0);
        assertThat(fileId.fileNum()).isEqualTo(111);
    }

    @Test
    void toFileIDWithNonZeroShardAndRealm() {
        final var entityId = EntityId.of(1L, 2L, 112L);

        final var fileId = Utils.toFileID(entityId);

        assertThat(fileId).isNotNull();
        assertThat(fileId.shardNum()).isEqualTo(1);
        assertThat(fileId.realmNum()).isEqualTo(2);
        assertThat(fileId.fileNum()).isEqualTo(112);
    }

    @Test
    void toFileIDWithZeroEntityId() {
        final var entityId = EntityId.EMPTY;

        final var fileId = Utils.toFileID(entityId);

        assertThat(fileId).isNotNull();
        assertThat(fileId.shardNum()).isEqualTo(0);
        assertThat(fileId.realmNum()).isEqualTo(0);
        assertThat(fileId.fileNum()).isEqualTo(0);
    }
}
