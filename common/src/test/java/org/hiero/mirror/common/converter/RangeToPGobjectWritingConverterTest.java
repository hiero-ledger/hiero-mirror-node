// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RangeToPGobjectWritingConverterTest {

    private final RangeToPGobjectWritingConverter converter = new RangeToPGobjectWritingConverter();
    private final PGobjectToRangeReadingConverter reader = new PGobjectToRangeReadingConverter();

    static Stream<Arguments> rangesAndExpected() {
        return Stream.of(
                Arguments.of(Range.closedOpen(1L, 5L), "[1,5)"),
                Arguments.of(Range.atLeast(5L), "[5,)"),
                // bound types are honored rather than forced to closed-lower/open-upper
                Arguments.of(Range.closed(1L, 5L), "[1,5]"),
                Arguments.of(Range.open(1L, 5L), "(1,5)"),
                Arguments.of(Range.openClosed(1L, 5L), "(1,5]"),
                Arguments.of(Range.greaterThan(5L), "(5,)"),
                Arguments.of(Range.lessThan(10L), "(,10)"),
                Arguments.of(Range.atMost(10L), "(,10]"),
                Arguments.of(Range.all(), "(,)"));
    }

    @ParameterizedTest
    @MethodSource("rangesAndExpected")
    void convert(Range<Long> source, String expected) {
        var pgObject = converter.convert(source);
        assertThat(pgObject).isNotNull();
        assertThat(pgObject.getType()).isEqualTo("int8range");
        assertThat(pgObject.getValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("rangesAndExpected")
    void roundTrip(Range<Long> source, String ignored) {
        assertThat(reader.convert(converter.convert(source))).isEqualTo(source);
    }

    @Test
    void convertNull() {
        assertThat(converter.convert(null)).isNull();
    }
}
