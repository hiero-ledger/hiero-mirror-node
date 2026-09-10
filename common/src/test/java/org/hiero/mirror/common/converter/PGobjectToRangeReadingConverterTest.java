// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PGobject;

class PGobjectToRangeReadingConverterTest {

    private final PGobjectToRangeReadingConverter converter = new PGobjectToRangeReadingConverter();

    static java.util.stream.Stream<Arguments> rangeStringsAndExpected() {
        return java.util.stream.Stream.of(
                Arguments.of("[1,5)", Range.closedOpen(1L, 5L)),
                Arguments.of("[5,)", Range.atLeast(5L)),
                Arguments.of("(,10)", Range.lessThan(10L)),
                // brackets are honored rather than assumed to be [), so canonical and non-canonical parse correctly
                Arguments.of("[1,5]", Range.closed(1L, 5L)),
                Arguments.of("(1,5)", Range.open(1L, 5L)),
                Arguments.of("(1,5]", Range.openClosed(1L, 5L)),
                Arguments.of("[5,]", Range.atLeast(5L)),
                // fully unbounded, which the previous split-based parser threw on
                Arguments.of("[,)", Range.all()));
    }

    @ParameterizedTest
    @MethodSource("rangeStringsAndExpected")
    void convert(String value, Range<Long> expected) {
        assertThat(converter.convert(pgObject(value))).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"empty", "EMPTY"})
    void convertReturnsNull(String value) {
        assertThat(converter.convert(pgObject(value))).isNull();
    }

    private PGobject pgObject(String value) {
        var pgObject = new PGobject();
        pgObject.setType("int8range");
        try {
            pgObject.setValue(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return pgObject;
    }
}
