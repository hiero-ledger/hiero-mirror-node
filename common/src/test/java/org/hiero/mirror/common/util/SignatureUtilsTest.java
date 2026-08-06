// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class SignatureUtilsTest {

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
