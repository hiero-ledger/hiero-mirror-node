// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import java.time.Instant;
import org.apache.commons.codec.binary.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    void isMirrorAddressReturnsTrue() {
        final var address = Address.fromHexString("0x00000000000000000000000000000000000004e4");
        assertTrue(Utils.isMirror(address));
    }

    @CsvSource(
            value = {
                "0, 0, 00000000000000000000000000000000000004e4, true",
                "1, 1, 00000001000000000000000100000000000004e4, true",
                "1, 0, 00000000000000000000000000000000000004e4, false",
                "0, 1, 00000000000000000000000000000000000004e4, false",
                "1, 1, 00000000000000000000000000000000000004e4, false",
                "0, 1, 00000001000000000000000100000000000004e4, false",
                "1, 0, 00000001000000000000000100000000000004e4, false",
                "0, 0, 000000000000000000000000000000000004e4, false",
                "0, 0, , false",
            })
    @ParameterizedTest
    void isMirror(long shard, long realm, String hexAddress, boolean result) throws Exception {
        CommonProperties.getInstance().setShard(shard);
        CommonProperties.getInstance().setRealm(realm);
        var address = hexAddress != null ? Hex.decodeHex(hexAddress) : null;
        assertThat(Utils.isMirror(address)).isEqualTo(result);
    }
}
