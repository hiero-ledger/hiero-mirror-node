// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.reader.block.hash.ShaMessageDigestFactory.SHA_256_SIZE;
import static org.hiero.mirror.importer.reader.block.hash.ShaMessageDigestFactory.SHA_384_SIZE;

import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

final class IncrementalStreamingHasherTest {

    static final byte[] EMPTY_TREE_SHA_256_HASH =
            Hex.decode("6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d");
    static final byte[] EMPTY_TREE_SHA_384_HASH = Hex.decode(
            "bec021b4f368e3069134e012c2b4307083d3a9bdd206e24e5f0d86e13d6636655933ec2b413465966817a9c208a11717");

    @ParameterizedTest(name = "digest size {0}")
    @CsvSource(textBlock = """
            32, 4d10054b665bb1695e7c1c633272cc0686a901bfcef0978a485f610dfe66cb0e
            48, c84d5ef5565ebd554d692d4a9500c7f328f05c0a661cc627a036dcb84f6563a27ceabf32fdf70c77e4c527f7490f2fa8
            """)
    void hash(final int digestSize, final String expected) {
        // given
        final var hasher = new IncrementalStreamingHasher(digestSize);
        hasher.addLeaf(new byte[] {0x0});
        hasher.addLeaf(new byte[] {0x1});
        hasher.addLeaf(new byte[] {0x2});

        // when, then
        assertThat(hasher.computeRootHash()).isEqualTo(Hex.decode(expected));
    }

    @Tag("Conformance constants")
    @ParameterizedTest(name = "digest size {0}")
    @MethodSource("provideEmptyTreeHashes")
    void hashEmptyTree(final int digestSize, final byte[] expectedEmptyTreeHash) {
        final var hasher = new IncrementalStreamingHasher(digestSize);
        assertThat(hasher.computeRootHash()).isEqualTo(expectedEmptyTreeHash);
        assertThat(IncrementalStreamingHasher.getEmptyTreeHash(digestSize)).isEqualTo(expectedEmptyTreeHash);
    }

    @Test
    void getEmptyTreeHashUnsupportedDigestSize() {
        assertThatThrownBy(() -> IncrementalStreamingHasher.getEmptyTreeHash(16))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported digest size 16");
    }

    @Test
    void unsupportedDigestSize() {
        assertThatThrownBy(() -> new IncrementalStreamingHasher(16))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported digest size 16");
    }

    static byte[] emptyTreeHash(final int digestSize) {
        return switch (digestSize) {
            case SHA_256_SIZE -> EMPTY_TREE_SHA_256_HASH;
            case SHA_384_SIZE -> EMPTY_TREE_SHA_384_HASH;
            default -> throw new IllegalArgumentException("Unsupported digest size " + digestSize);
        };
    }

    private static Stream<Arguments> provideEmptyTreeHashes() {
        return Stream.of(
                Arguments.of(SHA_256_SIZE, EMPTY_TREE_SHA_256_HASH),
                Arguments.of(SHA_384_SIZE, EMPTY_TREE_SHA_384_HASH));
    }
}
