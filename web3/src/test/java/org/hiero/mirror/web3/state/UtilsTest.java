// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX_CAPITAL;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hederahashgraph.api.proto.java.Key.KeyCase;
import java.time.Instant;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    private static Stream<Arguments> parseHexArguments() {
        return Stream.of(
                Arguments.of(""), // empty
                Arguments.of("0x"), // prefix only (lowercase)
                Arguments.of("0X"), // prefix only (uppercase)
                Arguments.of("00"), // normal, even
                Arguments.of("1234"), // normal, even
                Arguments.of("abcdef"), // normal, even, lowercase
                Arguments.of("ABCDEF"), // normal, even, uppercase
                Arguments.of("0x1234"), // normal, even, with lowercase prefix
                Arguments.of("0X1234"), // normal, even, with uppercase prefix
                Arguments.of("a"), // normal, odd, single nibble
                Arguments.of("123"), // normal, odd
                Arguments.of("0xabc"), // normal, odd, with prefix
                Arguments.of("zz"), // wrong hex, both nibbles invalid
                Arguments.of("12g4"), // wrong hex, single non-hex character
                Arguments.of("0xGG")); // wrong hex, with prefix
    }

    @ParameterizedTest
    @MethodSource("parseHexArguments")
    void parseHex(final String input) {
        final var result = Utils.parseHex(input);

        assertThat(result).isNotNull();

        // parseHex decodes any non-hex character to the nibble 'f', so replace invalid characters with 'f'
        // in the input. Bytes handles the optional prefix, odd-length padding and lower-casing.
        final var body =
                input.startsWith(HEX_PREFIX) || input.startsWith(HEX_PREFIX_CAPITAL) ? input.substring(2) : input;
        final var expected =
                Bytes.fromHexStringLenient(body.replaceAll("[^0-9a-fA-F]", "f")).toUnprefixedHexString();

        // Convert the produced bytes back to a hex string and compare with the expected input.
        final var roundTrip = Bytes.wrap(result).toUnprefixedHexString();
        assertThat(roundTrip).isEqualTo(expected);
    }
}
