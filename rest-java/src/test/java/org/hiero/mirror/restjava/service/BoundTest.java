// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.parameter.NumberRangeParameter;
import org.junit.jupiter.api.Test;

final class BoundTest {

    private static final String PARAM = "account.id";

    private static Bound bound(boolean primarySortField, NumberRangeParameter... params) {
        return new Bound(params, primarySortField, PARAM, null);
    }

    @Test
    void adjustsNormalBounds() {
        final var lower = bound(false, new NumberRangeParameter(RangeOperator.GT, 5L));
        assertThat(lower.getAdjustedLowerRangeValue()).isEqualTo(6L);

        final var upper = bound(false, new NumberRangeParameter(RangeOperator.LT, 5L));
        assertThat(upper.adjustUpperBound()).isEqualTo(4L);
    }

    @Test
    void rejectsGtAtLongMaxValue() {
        // Without the guard, GT Long.MAX_VALUE increments to Long.MIN_VALUE
        assertThatThrownBy(() -> bound(true, new NumberRangeParameter(RangeOperator.GT, Long.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid range");
    }

    @Test
    void rejectsLtAtLongMinValue() {
        // Without the guard, LT Long.MIN_VALUE decrements to Long.MAX_VALUE
        assertThatThrownBy(() -> bound(true, new NumberRangeParameter(RangeOperator.LT, Long.MIN_VALUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid range");
    }

    @Test
    void rejectsEdgeBoundEvenWhenNotPrimarySortField() {
        // The overflow guard fires during construction (via the adjust* calls), so it covers non-primary/secondary
        // bounds too, which the primarySortField-gated lower > upper check would otherwise skip.
        assertThatThrownBy(() -> bound(false, new NumberRangeParameter(RangeOperator.GT, Long.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bound(false, new NumberRangeParameter(RangeOperator.LT, Long.MIN_VALUE)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
