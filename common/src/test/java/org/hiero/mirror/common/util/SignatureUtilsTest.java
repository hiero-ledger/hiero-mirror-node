// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class SignatureUtilsTest {

    @Test
    void keccak256() {
        // Standard Keccak-256 test vectors (not NIST SHA3-256, which uses different padding and would produce a
        // different digest for the same input) -- the empty-input vector is Ethereum's well-known
        // empty-input/empty-RLP-list hash. Confirms SignatureUtils.keccak256 matches the real Ethereum-compatible
        // algorithm, not just internal self-consistency.
        assertThat(SignatureUtils.keccak256(new byte[0]))
                .isEqualTo(HexFormat.of().parseHex("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"));

        var abc = "abc".getBytes(StandardCharsets.UTF_8);
        assertThat(SignatureUtils.keccak256(abc))
                .isEqualTo(HexFormat.of().parseHex("4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"));

        // Multi-argument form concatenates its inputs before hashing, equivalent to a single call with the
        // concatenated bytes.
        var a = "a".getBytes(StandardCharsets.UTF_8);
        var bc = "bc".getBytes(StandardCharsets.UTF_8);
        assertThat(SignatureUtils.keccak256(a, bc)).isEqualTo(SignatureUtils.keccak256(abc));
    }

    @CsvSource(emptyValue = "", nullValues = "null", textBlock = """
            null,''
            '',''
            02ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c614023,''
            02ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310, efa0d905af20199aa03aca71cfa5f7647f29f439
            02ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402311,''
            02ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c6140231000,''
            """)
    @ParameterizedTest
    void recoverAddressFromPubKey(String input, String output) {
        final var keyBytes = input != null ? HexFormat.of().parseHex(input) : null;
        final var expected = HexFormat.of().parseHex(output);
        assertThat(SignatureUtils.recoverAddressFromPubKey(keyBytes)).isEqualTo(expected);
    }
}
