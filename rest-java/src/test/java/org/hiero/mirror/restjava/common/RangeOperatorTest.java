// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

final class RangeOperatorTest {

    @CsvSource({
        "EQ, true",
        "GT, false",
        "GTE, true",
        "LT, false",
        "LTE, true",
        "NE, false",
        "UNKNOWN, false",
    })
    @ParameterizedTest
    void isInclusive(RangeOperator operator, boolean expected) {
        assertThat(operator.isInclusive()).isEqualTo(expected);
    }

    @EnumSource(RangeOperator.class)
    @ParameterizedTest
    void testToString(RangeOperator operator) {
        assertThat(operator.toString()).isEqualTo(operator.name().toLowerCase(Locale.ROOT));
    }

    @EnumSource(value = RangeOperator.class, mode = EnumSource.Mode.EXCLUDE, names = "UNKNOWN")
    @ParameterizedTest
    void of(RangeOperator operator) {
        assertThat(RangeOperator.of(operator.name())).isEqualTo(operator);
        assertThat(RangeOperator.of(operator.name().toLowerCase(Locale.ROOT))).isEqualTo(operator);
    }

    @NullAndEmptySource
    @ParameterizedTest
    @ValueSource(strings = {"unknown", "UNKNOWN", " "})
    void ofInvalid(String name) {
        assertThatThrownBy(() -> RangeOperator.of(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @CsvSource({
        "EQ, EQ",
        "GT, GTE",
        "GTE, GTE",
        "LT, LTE",
        "LTE, LTE",
        "NE, NE",
        "UNKNOWN, UNKNOWN",
    })
    @ParameterizedTest
    void toInclusive(RangeOperator input, RangeOperator expected) {
        assertThat(input.toInclusive()).isEqualTo(expected);
    }
}
