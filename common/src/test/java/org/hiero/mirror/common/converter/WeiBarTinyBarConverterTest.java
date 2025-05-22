// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.math.BigInteger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class WeiBarTinyBarConverterTest {

    private static final WeiBarTinyBarConverter CONVERTER = WeiBarTinyBarConverter.INSTANCE;
    private static final Long DEFAULT_GAS = 1234567890123L;

    @Test
    void convertBytes() {
        var emptyBytes = new byte[] {};
        var bigInteger = BigInteger.valueOf(DEFAULT_GAS);
        var expectedBytes = BigInteger.valueOf(123).toByteArray();
        var expectedNegativeBytes = BigInteger.valueOf(-123).toByteArray();

        Assertions.assertThat(CONVERTER.convert(null, true)).isNull();
        Assertions.assertThat(CONVERTER.convert(null, false)).isNull();
        Assertions.assertThat(CONVERTER.convert(emptyBytes, true)).isSameAs(emptyBytes);
        Assertions.assertThat(CONVERTER.convert(emptyBytes, false)).isSameAs(emptyBytes);
        Assertions.assertThat(CONVERTER.convert(bigInteger.toByteArray(), true)).isEqualTo(expectedBytes);
        Assertions.assertThat(CONVERTER.convert(bigInteger.toByteArray(), false))
                .isEqualTo(expectedBytes);
        Assertions.assertThat(CONVERTER.convert(bigInteger.negate().toByteArray(), true))
                .isEqualTo(expectedNegativeBytes);
        Assertions.assertThat(CONVERTER.convert(bigInteger.negate().toByteArray(), false))
                .isNotEqualTo(expectedBytes)
                .isNotEqualTo(expectedNegativeBytes);
    }

    @Test
    void convertLong() {
        Assertions.assertThat(CONVERTER.convert(null)).isNull();
        Assertions.assertThat(CONVERTER.convert(DEFAULT_GAS)).isEqualTo(123L);
    }
}
