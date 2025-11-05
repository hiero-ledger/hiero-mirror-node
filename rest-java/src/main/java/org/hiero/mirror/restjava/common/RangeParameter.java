// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

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
}
