// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HexUtilsTest {

    @CsvSource({"'0x0',0", "'0x',0", "'0x100',256", "'0X65f9e0c0',1710874816", "'256',256", "' 16 ',16"})
    @ParameterizedTest
    void parseValue(String value, long expected) {
        assertThat(HexUtils.parseValue(value)).isEqualTo(expected);
    }

    @Test
    void parseValueAcceptsJsonNumbers() {
        assertThat(HexUtils.parseValue((Object) 7)).isEqualTo(7L);
        assertThat(HexUtils.parseValue((Object) 8L)).isEqualTo(8L);
        assertThat(HexUtils.parseValue((Object) BigInteger.TEN)).isEqualTo(10L);
        assertThat(HexUtils.parseValue((Object) "0x10")).isEqualTo(16L);
        assertThat(HexUtils.parseValue((Object) "-5")).isEqualTo(-5L);
    }

    @Test
    void parseValueRejectsOtherTypes() {
        assertThatThrownBy(() -> HexUtils.parseValue((Object) 1.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
