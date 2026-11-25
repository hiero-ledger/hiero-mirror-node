// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.stream.Stream;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class HexUtilsTest {

    @Test
    void convertValueToHexStringReturnsZeroForNull() {
        assertThat(HexUtils.convertValueToHexString(null)).isEqualTo("0x0");
    }

    @Test
    void convertValueToHexStringReturnsZeroForZeroWei() {
        assertThat(HexUtils.convertValueToHexString(Wei.ZERO)).isEqualTo("0x0");
    }

    static Stream<Arguments> weiValues() {
        return Stream.of(
                Arguments.of(Wei.ONE, "0x1"),
                Arguments.of(Wei.of(10), "0xa"),
                Arguments.of(Wei.of(16), "0x10"),
                Arguments.of(Wei.of(255), "0xff"),
                Arguments.of(Wei.of(256), "0x100"),
                Arguments.of(Wei.of(1000), "0x3e8"),
                Arguments.of(Wei.of(1_000_000), "0xf4240"),
                Arguments.of(Wei.of(new BigInteger("1000000000000000000")), "0xde0b6b3a7640000"));
    }

    @MethodSource("weiValues")
    @ParameterizedTest
    void convertValueToHexStringReturnsCorrectHexForNonZeroWei(Wei value, String expectedHex) {
        assertThat(HexUtils.convertValueToHexString(value)).isEqualTo(expectedHex);
    }

    @CsvSource({
        "0,0x0",
        "1,0x1",
        "10,0xa",
        "15,0xf",
        "16,0x10",
        "255,0xff",
        "256,0x100",
        "1000,0x3e8",
        "65535,0xffff",
        "1000000,0xf4240",
        "9223372036854775807,0x7fffffffffffffff"
    })
    @ParameterizedTest
    void convertLongToHexString(long number, String expectedHex) {
        assertThat(HexUtils.convertLongToHexString(number)).isEqualTo(expectedHex);
    }

    @Test
    void convertLongToHexStringHandlesNegativeNumber() {
        // Long.toHexString treats the long as unsigned, so -1 becomes all f's
        assertThat(HexUtils.convertLongToHexString(-1)).isEqualTo("0xffffffffffffffff");
    }
}
