// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class EntityIdFromStringConverterTest {

    @ParameterizedTest(name = "Convert \"{0}\" to EntityId")
    @CsvSource({"1.2.3, 1.2.3", "0.0.1001, 0.0.1001"})
    void testConverter(String source, String expected) {
        var converter = new EntityIdFromStringConverter();
        assertThat(converter.convert(source)).hasToString(expected);
    }

    @ParameterizedTest(name = "Convert \"{0}\" to EntityId")
    @NullAndEmptySource
    void testInvalidSource(String source) {
        var converter = new EntityIdFromStringConverter();
        assertThat(converter.convert(source)).isNull();
    }
}
