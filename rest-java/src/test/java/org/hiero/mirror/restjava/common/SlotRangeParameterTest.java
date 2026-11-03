// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.restjava.parameter.SlotRangeParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

final class SlotRangeParameterTest {

    private static final String MAX_SLOT = StringUtils.repeat('f', 64);
    private static final String ZERO_SLOT = StringUtils.repeat('0', 64);

    @ParameterizedTest
    @NullAndEmptySource
    void empty(String input) throws Exception {
        assertThat(SlotRangeParameter.valueOf(input)).isEqualTo(SlotRangeParameter.EMPTY);
    }

    @Test
    void inclusiveValue() throws Exception {
        var gt = SlotRangeParameter.valueOf("gt:0x01");
        assertThat(gt.operator()).isEqualTo(RangeOperator.GTE);
        assertThat(Hex.encodeHexString(gt.value())).isEqualTo(StringUtils.leftPad("2", 64, '0'));

        var lt = SlotRangeParameter.valueOf("lt:0x02");
        assertThat(lt.operator()).isEqualTo(RangeOperator.LTE);
        assertThat(Hex.encodeHexString(lt.value())).isEqualTo(StringUtils.leftPad("1", 64, '0'));

        var lte = SlotRangeParameter.valueOf("lte:" + MAX_SLOT);
        assertThat(lte.operator()).isEqualTo(RangeOperator.LTE);
        assertThat(Hex.encodeHexString(lte.value())).isEqualTo(MAX_SLOT);

        var gte = SlotRangeParameter.valueOf("gte:0");
        assertThat(gte.operator()).isEqualTo(RangeOperator.GTE);
        assertThat(Hex.encodeHexString(gte.value())).isEqualTo(ZERO_SLOT);
    }

    @ParameterizedTest
    @DisplayName("Exclusive bounds past the domain edge are out of range and rejected")
    @ValueSource(
            strings = {
                "gt:0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "gt:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                "lt:0x0",
                "lt:0000000000000000000000000000000000000000000000000000000000000000"
            })
    void edgeExclusiveBoundsRejected(String input) {
        assertThrows(IllegalArgumentException.class, () -> SlotRangeParameter.valueOf(input));
    }

    @Test
    @DisplayName("Near-edge exclusive bounds still convert to inclusive without wrapping")
    void nearEdgeStillConverts() throws Exception {
        final var almostMax = "f".repeat(63) + "e";
        final var gt = SlotRangeParameter.valueOf("gt:0x" + almostMax);
        assertThat(gt.operator()).isEqualTo(RangeOperator.GTE);
        assertThat(Hex.encodeHexString(gt.value())).isEqualTo(MAX_SLOT);

        final var almostMin = "0".repeat(63) + "1";
        final var lt = SlotRangeParameter.valueOf("lt:0x" + almostMin);
        assertThat(lt.operator()).isEqualTo(RangeOperator.LTE);
        assertThat(Hex.encodeHexString(lt.value())).isEqualTo(ZERO_SLOT);
    }

    @Test
    void invalidPatternRejected() {
        assertThrows(IllegalArgumentException.class, () -> SlotRangeParameter.valueOf("gt:xyz"));
    }
}
