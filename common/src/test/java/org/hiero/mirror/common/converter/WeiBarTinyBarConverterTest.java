// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

final class WeiBarTinyBarConverterTest {

    private static final WeiBarTinyBarConverter CONVERTER = WeiBarTinyBarConverter.INSTANCE;
    private static final Long DEFAULT_GAS = 1234567890123L;

    @Test
    void convertBytes() {
        var emptyBytes = new byte[] {};
        var bigInteger = BigInteger.valueOf(DEFAULT_GAS);
        var expected = BigInteger.valueOf(123);
        var expectedNegative = BigInteger.valueOf(-123);

        assertThat(CONVERTER.convert(null, true)).isNull();
        assertThat(CONVERTER.convert(null, false)).isNull();
        assertThat(CONVERTER.convert(emptyBytes, true)).isNull();
        assertThat(CONVERTER.convert(emptyBytes, false)).isNull();
        assertThat(CONVERTER.convert(bigInteger.toByteArray(), true)).isEqualTo(expected);
        assertThat(CONVERTER.convert(bigInteger.toByteArray(), false)).isEqualTo(expected);
        assertThat(CONVERTER.convert(bigInteger.negate().toByteArray(), true)).isEqualTo(expectedNegative);
        assertThat(CONVERTER.convert(bigInteger.negate().toByteArray(), false))
                .isNotEqualTo(expected)
                .isNotEqualTo(expectedNegative);
    }

    @Test
    void convertLong() {
        assertThat(CONVERTER.convert(null)).isNull();
        assertThat(CONVERTER.convert(DEFAULT_GAS)).isEqualTo(123L);
    }
}
