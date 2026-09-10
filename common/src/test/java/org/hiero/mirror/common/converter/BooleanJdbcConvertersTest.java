// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BooleanJdbcConvertersTest {

    private final BooleanJdbcConverters.IntegerToBoolean converter = new BooleanJdbcConverters.IntegerToBoolean();

    @ParameterizedTest
    @CsvSource({
        // any non-zero value is truthy, matching Hibernate's BooleanJavaType
        "0, false",
        "1, true",
        "2, true",
        "-1, true",
    })
    void convert(int source, boolean expected) {
        assertThat(converter.convert(source)).isEqualTo(expected);
    }

    @Test
    void convertNull() {
        assertThat(converter.convert(null)).isNull();
    }
}
