// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Range;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class RangeUtilsTest {

    static Stream<Arguments> ranges() {
        return Stream.of(
                Arguments.of(Range.closedOpen(1L, 100L), "[1,100)"),
                Arguments.of(Range.closed(1L, 100L), "[1,100]"),
                Arguments.of(Range.open(1L, 100L), "(1,100)"),
                Arguments.of(Range.openClosed(1L, 100L), "(1,100]"),
                Arguments.of(Range.atLeast(1L), "[1,)"),
                Arguments.of(Range.greaterThan(1L), "(1,)"),
                Arguments.of(Range.atMost(100L), "(,100]"),
                Arguments.of(Range.lessThan(100L), "(,100)"),
                Arguments.of(Range.closedOpen(-5L, 0L), "[-5,0)"),
                Arguments.of(Range.singleton(5L), "[5,5]"),
                Arguments.of(
                        Range.closed(Long.MIN_VALUE, Long.MAX_VALUE), "[-9223372036854775808,9223372036854775807]"),
                Arguments.of(Range.atLeast(Long.MIN_VALUE), "[-9223372036854775808,)"),
                Arguments.of(Range.atMost(Long.MAX_VALUE), "(,9223372036854775807]"),
                Arguments.of(Range.all(), "(,)"));
    }

    @MethodSource("ranges")
    @ParameterizedTest
    void rangeLong(Range<Long> expected, String text) {
        final var range = RangeUtils.rangeLong(text);
        assertThat(range).isEqualTo(expected);
        assertThat(RangeUtils.rangeToString(range)).isEqualTo(text);
    }

    @Test
    void rangeLongEmpty() {
        final var range = RangeUtils.rangeLong("empty");
        assertThat(range).isEqualTo(Range.closedOpen(Long.MIN_VALUE, Long.MIN_VALUE));
        assertThat(RangeUtils.rangeToString(range)).isEqualTo("empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[5,5)", "(5,5]"})
    void rangeLongEmptyEndpoints(String text) {
        final var range = RangeUtils.rangeLong(text);
        // Guava preserves endpoints, so these ranges are empty but not equal to rangeLong("empty").
        assertThat(range.isEmpty()).isTrue();
        assertThat(RangeUtils.rangeToString(range)).isEqualTo("empty");
    }

    @EmptySource
    @ParameterizedTest
    @ValueSource(strings = {"[1)", "1,5", "[12,5", "1,5)", ",", "[,", "[1,", "[1,2,3)", "[a,5)"})
    void rangeLongInvalid(String text) {
        assertThatThrownBy(() -> RangeUtils.rangeLong(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"(5,5)", "[6,5]"})
    void rangeLongInvalidEndpoints(String text) {
        // Guava rejects open ranges with equal endpoints, such as (5,5), and inverted bounds, such as [6,5].
        assertThatThrownBy(() -> RangeUtils.rangeLong(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangeLongNull() {
        assertThatThrownBy(() -> RangeUtils.rangeLong(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[-9223372036854775809,0)",
                "[0,9223372036854775808)",
                "[9223372036854775808,)",
                "(,-9223372036854775809]"
            })
    void rangeLongOverflow(String text) {
        assertThatThrownBy(() -> RangeUtils.rangeLong(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @MethodSource("ranges")
    @ParameterizedTest
    void rangeToString(Range<Long> range, String expected) {
        assertThat(RangeUtils.rangeToString(range)).isEqualTo(expected);
    }

    @Test
    void rangeToStringBigDecimal() {
        final var range = Range.openClosed(new BigDecimal("-1.25"), new BigDecimal("5.75"));
        assertThat(RangeUtils.rangeToString(range)).isEqualTo("(-1.25,5.75]");
    }

    @Test
    void rangeToStringEmpty() {
        assertThat(RangeUtils.rangeToString(Range.closedOpen(1L, 1L))).isEqualTo("empty");
    }

    @Test
    void rangeToStringInteger() {
        assertThat(RangeUtils.rangeToString(Range.closedOpen(1, 5))).isEqualTo("[1,5)");
    }

    @Test
    void rangeToStringNull() {
        assertThat(RangeUtils.rangeToString(null)).isNull();
    }
}
