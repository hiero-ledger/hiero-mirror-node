// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BytesDecoderTest {

    @CsvSource(textBlock = """
              0x08c379a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000047465737400000000000000000000000000000000000000000000000000000000,                                                                                         test
              0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000,                                                                                         Custom revert message
              0x08c379a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000304d65737361676520776974682073796d626f6c7320616e64206e756d62657273202d2021203120322033202a2026203f00000000000000000000000000000000,                         Message with symbols and numbers - ! 1 2 3 * & ?
              0x08c379a0,                                                                                                                                                                                                                                                                                         ''
              0x,                                                                                                                                                                                                                                                                                                 ''
              '',                                                                                                                                                                                                                                                                                                 ''
            """)
    @ParameterizedTest
    void convertHexString(String input, String output) {
        assertThat(BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage(input))
                .isEqualTo(output);
    }

    @Test
    void convertHexStringWithNullValue() {
        assertThat(BytesDecoder.maybeDecodeSolidityErrorStringToReadableMessage(null))
                .isEmpty();
    }

    @CsvSource(textBlock = """
              test,                                                                                         0x08c379a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000047465737400000000000000000000000000000000000000000000000000000000
              Custom revert message,                                                                        0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000
              Message with symbols and numbers - ! 1 2 3 * & ?,                                             0x08c379a0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000304d65737361676520776974682073796d626f6c7320616e64206e756d62657273202d2021203120322033202a2026203f00000000000000000000000000000000
              '',                                                                                           0x
              0x,                                                                                           0x
              0x08c379a0,                                                                                   0x08c379a0
            """)
    @ParameterizedTest
    void getAbiEncodedRevertReason(String input, String output) {
        assertThat(BytesDecoder.getAbiEncodedRevertReason(input)).isEqualTo(output);
    }

    @Test
    void getAbiEncodedRevertReasonWithNullString() {
        assertThat(BytesDecoder.getAbiEncodedRevertReason(null)).isEqualTo(HEX_PREFIX);
    }
}
