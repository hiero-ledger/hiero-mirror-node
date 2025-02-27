// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.CommonProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NumberRangeParameterTest {

    private final CommonProperties commonProperties = new CommonProperties();

    @BeforeEach
    void setup() {
        EntityIdParameter.PROPERTIES.set(commonProperties);
    }

    @Test
    void testNoOperatorPresent() {
        assertThat(new NumberRangeParameter(RangeOperator.EQ, 2000L)).isEqualTo(NumberRangeParameter.valueOf("2000"));
    }

    @ParameterizedTest
    @EnumSource(RangeOperator.class)
    void testRangeOperator(RangeOperator operator) {
        assertThat(new NumberRangeParameter(operator, 2000L))
                .isEqualTo(NumberRangeParameter.valueOf(operator + ":2000"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testEmpty(String input) {
        assertThat(NumberRangeParameter.valueOf(input)).isEqualTo(NumberRangeParameter.EMPTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", ".1", "someinvalidstring", "-1", "9223372036854775808", ":2000", ":", "eq:", ":1"})
    @DisplayName("IntegerRangeParameter parse from string tests, negative cases")
    void testInvalidParam(String input) {
        assertThrows(IllegalArgumentException.class, () -> NumberRangeParameter.valueOf(input));
    }
}
