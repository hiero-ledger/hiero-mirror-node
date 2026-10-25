// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

final class DataSizeMaxValidatorTest {

    @ParameterizedTest(name = "max={0} {1}, value={2} -> {3}")
    @CsvSource(textBlock = """
            # max, unit,  value,    expected
            1, MEGABYTES,          , true
            1, MEGABYTES, 0B       , true
            1, MEGABYTES, 1048575B , true
            1, MEGABYTES, 1048576B , true
            1, MEGABYTES, 1048577B , false
            1, MEGABYTES, 1024KB   , true
            1, MEGABYTES, 1025KB   , false
            1, MEGABYTES, 1MB      , true
            1, MEGABYTES, 2MB      , false
            1, MEGABYTES, 1GB      , false
            0, BYTES,     -1B      , true
            0, BYTES,     0B       , true
            0, BYTES,     1B       , false
            2, GIGABYTES, 2048MB   , true
            2, GIGABYTES, 2049MB   , false
            """)
    void isValid(long max, DataUnit unit, String value, boolean expected) {
        final var annotation = mock(DataSizeMax.class);
        when(annotation.value()).thenReturn(max);
        when(annotation.unit()).thenReturn(unit);

        final var validator = new DataSizeMaxValidator();
        validator.initialize(annotation);

        final var dataSize = value != null ? DataSize.parse(value) : null;
        assertThat(validator.isValid(dataSize, null)).isEqualTo(expected);
    }
}
