// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import java.util.function.BiFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.Field;

@Getter
@RequiredArgsConstructor
@SuppressWarnings({"rawtypes", "unchecked"})
public enum RangeOperator {
    EQ("=", Field::eq),
    GT(">", Field::gt),
    GTE(">=", Field::ge),
    LT("<", Field::lt),
    LTE("<=", Field::le),
    NE("!=", Field::ne),
    UNKNOWN("unknown", null);

    private final String operator;
    private final BiFunction<Field, Object, Condition> function;

    public boolean isInclusive() {
        return this == RangeOperator.EQ || this == RangeOperator.LTE || this == RangeOperator.GTE;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    public static RangeOperator of(String rangeOperator) {
        try {
            if (StringUtils.isBlank(rangeOperator)) {
                throw invalidOperator(rangeOperator);
            }

            final var operator = RangeOperator.valueOf(rangeOperator.toUpperCase());
            if (operator == UNKNOWN) {
                throw invalidOperator(rangeOperator);
            }
            return operator;
        } catch (IllegalArgumentException e) {
            throw invalidOperator(rangeOperator);
        }
    }

    public RangeOperator toInclusive() {
        return switch (this) {
            case GT -> GTE;
            case LT -> LTE;
            default -> this;
        };
    }

    private static IllegalArgumentException invalidOperator(String rangeOperator) {
        final var name = rangeOperator != null ? rangeOperator.toLowerCase() : null;
        return new IllegalArgumentException(
                "Invalid range operator %s. Valid values: eq, gt, gte, lt, lte, ne".formatted(name));
    }
}
