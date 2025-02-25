// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.converter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StringToLongConverterTest {

    @ParameterizedTest(name = "Convert \"{0}\" to Long")
    @CsvSource(delimiter = ',', textBlock = """
            1, 1
            0, 0
            0.0.2, 2
            """)
    void testConverter(String source, Long expected) {
        var converter = new StringToLongConverter();
        Long actual = converter.convert(source);
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest(name = "Convert \"{0}\" to Long")
    @NullAndEmptySource
    void testInvalidSource(String source) {
        var converter = new StringToLongConverter();
        assertThat(converter.convert(source)).isNull();
    }

    @ParameterizedTest(name = "Fail to convert \"{0}\" to Long")
    @ValueSource(strings = {"bad", "1.557", "5444.0"})
    void testConverterFailures(String source) {
        var converter = new StringToLongConverter();
        assertThrows(NumberFormatException.class, () -> converter.convert(source));
    }
}
