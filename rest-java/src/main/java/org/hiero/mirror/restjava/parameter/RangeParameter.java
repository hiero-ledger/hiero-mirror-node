// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import org.hiero.mirror.restjava.common.RangeOperator;

public interface RangeParameter<T> {

    RangeOperator operator();

    T value();

    // Considering EQ in the same category as GT,GTE as an assumption
    default boolean hasLowerBound() {
        return operator() == RangeOperator.GT || operator() == RangeOperator.GTE || operator() == RangeOperator.EQ;
    }

    default boolean hasUpperBound() {
        return operator() == RangeOperator.LT || operator() == RangeOperator.LTE;
    }

    default boolean isEmpty() {
        return RangeOperator.UNKNOWN.equals(operator());
    }

    // Converts an exclusive operator (GT/LT) to its inclusive value, guarding against overflow
    static long toInclusive(RangeOperator operator, long value) {
        if (operator == RangeOperator.GT) {
            if (value == Long.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid range");
            }
            return value + 1;
        } else if (operator == RangeOperator.LT) {
            if (value == Long.MIN_VALUE) {
                throw new IllegalArgumentException("Invalid range");
            }
            return value - 1;
        } else {
            return value;
        }
    }
}
