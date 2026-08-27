// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ByteUtilsTest {

    @Test
    void wrapToWordSizeReturnsNullForNullInput() {
        assertThat(ByteUtils.wrapToWordSize(null)).isNull();
    }

    @Test
    void wrapToWordSizeReturnsZeroPaddedForEmptyArray() {
        var result = ByteUtils.wrapToWordSize(new byte[0]);

        assertThat(result).isEqualTo("0x" + "0".repeat(64));
    }

    @Test
    void wrapToWordSizePadsSingleByte() {
        var result = ByteUtils.wrapToWordSize(new byte[] {0x01});

        assertThat(result).isEqualTo("0x" + "0".repeat(62) + "01");
    }

    @Test
    void wrapToWordSizePadsMultipleBytes() {
        var result = ByteUtils.wrapToWordSize(new byte[] {0x12, 0x34});

        assertThat(result).isEqualTo("0x" + "0".repeat(60) + "1234");
    }

    @Test
    void wrapToWordSizeNosPaddingForExactly32Bytes() {
        var bytes = new byte[32];
        bytes[0] = 0x01;
        bytes[31] = (byte) 0xff;

        var result = ByteUtils.wrapToWordSize(bytes);

        assertThat(result).startsWith("0x01");
        assertThat(result).endsWith("ff");
        assertThat(result).hasSize(66); // 0x + 64 hex chars
    }

    @Test
    void wrapToWordSizeNoPaddingForMoreThan32Bytes() {
        var bytes = new byte[33];
        bytes[0] = 0x01;
        bytes[32] = (byte) 0xab;

        var result = ByteUtils.wrapToWordSize(bytes);

        assertThat(result).startsWith("0x01");
        assertThat(result).endsWith("ab");
        assertThat(result).hasSize(68); // 0x + 66 hex chars
    }

    @ParameterizedTest
    @CsvSource({
        "1, 0x0000000000000000000000000000000000000000000000000000000000000001",
        "16, 0x0000000000000000000000000000000000000000000000000000000000000010",
        "255, 0x00000000000000000000000000000000000000000000000000000000000000ff",
        "256, 0x0000000000000000000000000000000000000000000000000000000000000100"
    })
    void wrapToWordSizeHandlesVariousValues(int value, String expected) {
        var bytes = new byte[] {(byte) (value >> 8), (byte) value};
        if (value < 256) {
            bytes = new byte[] {(byte) value};
        }

        var result = ByteUtils.wrapToWordSize(bytes);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wrapToWordSizeHandlesMaxValueByte() {
        var result = ByteUtils.wrapToWordSize(new byte[] {(byte) 0xff});

        assertThat(result).isEqualTo("0x" + "0".repeat(62) + "ff");
    }

    @Test
    void wrapToWordSizeHandlesLeadingZeros() {
        var result = ByteUtils.wrapToWordSize(new byte[] {0x00, 0x00, 0x01});

        assertThat(result).isEqualTo("0x" + "0".repeat(58) + "000001");
    }
}
