// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.hiero.mirror.restjava.common.RangeOperator.EQ;
import static org.hiero.mirror.restjava.common.RangeOperator.GT;
import static org.hiero.mirror.restjava.common.RangeOperator.LT;
import static org.jooq.impl.DSL.noCondition;

import java.util.Arrays;
import java.util.List;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.parameter.RangeParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.jooq.Condition;
import org.jooq.Field;

interface JooqRepository {

    default Condition getCondition(Field<Long> field, RangeOperator operator, Long value) {
        return operator.getFunction().apply(field, value);
    }

    private Condition getCondition(RangeParameter<Long> param, Field<Long> field) {
        if (param == null || param.isEmpty()) {
            return noCondition();
        }

        return getCondition(field, param.operator(), param.value());
    }

    private Condition getCondition(Bound bound) {
        var field = bound.getField();
        return getCondition(bound.getLower(), field).and(getCondition(bound.getUpper(), field));
    }

    default Condition getBoundConditions(List<Bound> bounds) {
        return getBoundConditions(bounds, false, false);
    }

    /**
     * Produces the bound conditions by recursively iterating through the bounds
     *
     * Example:
     *       Primary Bound:    receiver_account_id: GTE 2000, LTE 3000
     *       Secondary Bounds: token_id:            GTE 4000, LTE 5000
     *                         serial_number:       GTE 5,    LTE 100
     *
     * Returns:
     *  [Lower Outer Condition]
     *  ("receiver_account_id" = 2000 and
     *       (
     *           ("token_id" = 4000 and "serial_number" >= 5) or
     *           "token_id" > 4000
     *        )
     *  )
     *  [Middle Condition]
     *  or ("receiver_account_id" > 2000 and "receiver_account_id" < 3000)
     *  [Upper Outer Condition]
     *  or ("receiver_account_id" = 3000 and
     *       (
     *           "token_id" < 5000 or
     *           ("token_id" = 5000 and "serial_number" <= 100)
     *       )
     *  )
     */
    private Condition getBoundConditions(List<Bound> bounds, boolean lowerProcessed, boolean upperProcessed) {
        if (bounds == null || bounds.isEmpty()) {
            return noCondition();
        }

        var primary = bounds.getFirst();
        if (bounds.size() == 1) {
            return getCondition(primary);
        }

        var secondaryBounds = bounds.subList(1, bounds.size());
        if (!lowerProcessed) {
            for (var bound : secondaryBounds) {
                // Only secondary bounds should be adjusted
                bound.adjustUpperRange();
            }
        }

        // Lower conditions need to be discovered before upper conditions because the methods involved update the
        // primary bound
        var lowerCondition = getOuterCondition(primary, secondaryBounds, false, lowerProcessed);
        var middleCondition = getMiddleCondition(primary, secondaryBounds);
        var upperCondition = getOuterCondition(primary, secondaryBounds, true, upperProcessed);

        return lowerCondition.or(middleCondition).or(upperCondition);
    }

    /**
     * Produces the lower or upper conditions by recursively iterating through the bounds.
     *
     * Example:
     *       Primary Bound:    receiver_account_id: GTE 2000, LTE 3000
     *       Secondary Bounds: token_id:            GTE 4000, LTE 5000
     *                         serial_number:       GTE 5,    LTE 100
     *  Returns this lower outer condition (when isUpper=false):
     *
     *  ("receiver_account_id" = 2000 and
     *       (
     *           ("token_id" = 4000 and "serial_number" >= 5) or
     *           "token_id" > 4000
     *       )
     *  )
     *
     *  Returns this upper outer condition (when isUpper=true):
     *
     *  ("receiver_account_id" = 3000 and
     *       (
     *           "token_id" < 5000 or
     *           ("token_id" = 5000 and "serial_number" <= 100)
     *       )
     *  )
     */
    private Condition getOuterCondition(
            Bound primary, List<Bound> secondaryBounds, boolean isUpper, boolean processed) {
        var outerCondition = getPrimaryCondition(primary, isUpper);
        if (outerCondition != noCondition()) {
            var outerBounds = processed ? secondaryBounds : removeRanges(secondaryBounds, isUpper);
            outerCondition = outerCondition.and(getBoundConditions(outerBounds, true, isUpper));
        }

        return outerCondition;
    }

    // Returns a list of new bounds that have had their lower or upper ranges removed
    private List<Bound> removeRanges(List<Bound> bounds, boolean isUpper) {
        return bounds.stream().map(b -> isUpper ? b.toUpper() : b.toLower()).toList();
    }

    private Condition getPrimaryCondition(Bound primary, boolean isUpper) {
        var rangeParameter = isUpper ? primary.getUpper() : primary.adjustLowerRange();
        if (rangeParameter == null || rangeParameter.isEmpty() || rangeParameter.operator() == EQ) {
            return noCondition();
        } else {
            long value = primary.getInclusiveRangeValue(isUpper);
            return getCondition(primary.getField(), EQ, value);
        }
    }

    /**
     * Produces the middle conditions by getting the primary condition and cumulating the secondary bounds if any
     * secondary bound.contains the EQ operator
     *
     * Example:
     *       Primary Bound:    receiver_account_id: GTE 2000, LTE 3000
     *       Secondary Bounds: token_id:            GTE 4000, LTE 5000
     *                         serial_number:       GTE 5,    LTE 100
     *
     * Returns:
     *  ("receiver_account_id" > 2000 and "receiver_account_id" < 3000)
     *
     * Example:
     *       Primary Bound:    receiver_account_id: GTE 2000, LTE 3000
     *       Secondary Bounds: token_id:            EQ 4000,  null
     *                         serial_number:       EQ 5,     null
     *
     * Returns:
     * ("receiver_account_id" > 2000 and "receiver_account_id" < 3000
     *   and "token_id" = 4000 and "serial_number" = 5)
     */
    private Condition getMiddleCondition(Bound primaryBound, List<Bound> secondaryBounds) {
        var primaryLower = primaryBound.getLower();
        var primaryUpper = primaryBound.getUpper();
        var field = primaryBound.getField();
        var primaryLowerCondition = getPrimaryMiddleCondition(primaryLower, field, GT);
        var primaryUpperCondition = getPrimaryMiddleCondition(primaryUpper, field, LT);

        var secondaryCondition = noCondition();
        for (var secondaryBound : secondaryBounds) {
            if (containsEqOperator(primaryLower, primaryUpper, secondaryBound.getUpper(), secondaryBound.getLower())) {
                secondaryCondition = secondaryCondition.and(getCondition(secondaryBound));
            }
        }

        return primaryLowerCondition.and(primaryUpperCondition).and(secondaryCondition);
    }

    private Condition getPrimaryMiddleCondition(
            RangeParameter<Long> rangeParameter, Field<Long> field, RangeOperator inclusiveOperator) {
        var condition = noCondition();
        if (rangeParameter != null && !rangeParameter.isEmpty()) {
            // When the primary param operator is EQ don't adjust the value for the primary param.
            if (rangeParameter.operator() == EQ) {
                condition = getCondition(rangeParameter, field);
            } else if (rangeParameter.operator().isInclusive()) {
                condition = getCondition(field, inclusiveOperator, rangeParameter.value());
            } else {
                condition = getCondition(rangeParameter, field);
            }
        }

        return condition;
    }

    private boolean containsEqOperator(RangeParameter<?>... rangeParameters) {
        return Arrays.stream(rangeParameters).anyMatch(this::hasEqOperator);
    }

    private boolean hasEqOperator(RangeParameter<?> rangeParameter) {
        return rangeParameter != null && rangeParameter.operator() == EQ;
    }
}
