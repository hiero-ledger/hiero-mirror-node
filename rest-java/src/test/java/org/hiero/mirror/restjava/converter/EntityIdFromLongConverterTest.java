// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

class EntityIdFromLongConverterTest {

    @ParameterizedTest(name = "Convert {0} to EntityId")
    @CsvSource({"0, 0.0.0", "1, 0.0.1", "1001, 0.0.1001"})
    void testConverter(Long source, String expected) {
        var converter = new EntityIdFromLongConverter();
        assertThat(converter.convert(source)).hasToString(expected);
    }

    @ParameterizedTest(name = "Convert {0} to EntityId")
    @NullSource
    void testNullSource(Long source) {
        var converter = new EntityIdFromLongConverter();
        assertThat(converter.convert(source)).isNull();
    }
}
