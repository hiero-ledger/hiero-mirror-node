// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

final class DataSizeMinValidatorTest {

    @ParameterizedTest(name = "min={0} {1}, value={2} -> {3}")
    @CsvSource(textBlock = """
            # min, unit,  value,     expected
            1, MEGABYTES,          , true
            1, MEGABYTES, 0B       , false
            1, MEGABYTES, 1048575B , false
            1, MEGABYTES, 1048576B , true
            1, MEGABYTES, 1023KB   , false
            1, MEGABYTES, 1024KB   , true
            1, MEGABYTES, 1MB      , true
            1, MEGABYTES, 1GB      , true
            0, BYTES,     0B       , true
            0, BYTES,     -1B      , false
            1, BYTES,     0B       , false
            1, BYTES,     1B       , true
            2, GIGABYTES, 2047MB   , false
            2, GIGABYTES, 2GB      , true
            """)
    void isValid(long min, DataUnit unit, String value, boolean expected) {
        final var annotation = mock(DataSizeMin.class);
        when(annotation.value()).thenReturn(min);
        when(annotation.unit()).thenReturn(unit);

        final var validator = new DataSizeMinValidator();
        validator.initialize(annotation);

        final var dataSize = value != null ? DataSize.parse(value) : null;
        assertThat(validator.isValid(dataSize, null)).isEqualTo(expected);
    }
}
