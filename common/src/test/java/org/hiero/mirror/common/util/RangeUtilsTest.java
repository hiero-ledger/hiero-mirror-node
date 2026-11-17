// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Range;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

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
                Arguments.of(Range.all(), "(,)"));
    }

    @MethodSource("ranges")
    @ParameterizedTest
    void rangeLong(Range<Long> expected, String text) {
        assertThat(RangeUtils.rangeLong(text)).isEqualTo(expected);
    }

    @Test
    void rangeLongEmpty() {
        assertThat(RangeUtils.rangeLong("empty")).isEqualTo(Range.closedOpen(Long.MIN_VALUE, Long.MIN_VALUE));
    }

    @Test
    void rangeLongInvalid() {
        assertThatThrownBy(() -> RangeUtils.rangeLong("[1)")).isInstanceOf(IllegalArgumentException.class);
    }

    @NullAndEmptySource
    @ParameterizedTest
    void rangeLongNullOrEmpty(String text) {
        assertThat(RangeUtils.rangeLong(text)).isNull();
    }

    @MethodSource("ranges")
    @ParameterizedTest
    void rangeToString(Range<Long> range, String expected) {
        assertThat(RangeUtils.rangeToString(range)).isEqualTo(expected);
    }

    @Test
    void rangeToStringEmpty() {
        assertThat(RangeUtils.rangeToString(Range.closedOpen(1L, 1L))).isEqualTo("empty");
    }

    @Test
    void rangeToStringNull() {
        assertThat(RangeUtils.rangeToString(null)).isNull();
    }
}
