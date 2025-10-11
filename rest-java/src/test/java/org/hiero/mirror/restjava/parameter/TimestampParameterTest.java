// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class TimestampParameterTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void empty(String input) {
        assertThat(TimestampParameter.valueOf(input)).isEqualTo(TimestampParameter.EMPTY);
    }

    @Test
    void noOperator() {
        assertThat(new TimestampParameter(RangeOperator.EQ, 0L)).isEqualTo(TimestampParameter.valueOf("0.0"));
    }

    @ParameterizedTest
    @CsvSource({
        "1, eq, 1, 0",
        "1.1, eq, 1, 1",
        "eq:1.1, eq, 1, 1",
        "lt:1234567890.999999999, lt, 1234567890, 999999999",
    })
    void valid(String input, String operator, Long seconds, Long nanos) {
        assertThat(new TimestampParameter(RangeOperator.of(operator), DomainUtils.convertToNanos(seconds, nanos)))
                .isEqualTo(TimestampParameter.valueOf(input));
    }

    @ParameterizedTest
    @EnumSource(
            value = RangeOperator.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"NE"})
    void rangeOperator(RangeOperator operator) {
        assertThat(new TimestampParameter(operator, 0L)).isEqualTo(TimestampParameter.valueOf(operator + ":0"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                ".1",
                "-1",
                ":1",
                "31556889864403200", // Max Instant epoch seconds + 1
                "1.1234567890",
                ":2000.1",
                ":",
                "a",
                "eq",
                "eq:",
                "eq:1:1",
                "eq:1.-1",
                "eq:-1.1",
                "gt:-1",
                "ne:1.1",
                "lt:ab",
                "invalid",
            })
    void invalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> TimestampParameter.valueOf(input));
    }
}
