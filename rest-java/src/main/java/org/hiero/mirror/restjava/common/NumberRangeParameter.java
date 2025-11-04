// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import org.apache.commons.lang3.StringUtils;

public record NumberRangeParameter(RangeOperator operator, Long value) implements RangeParameter<Long> {

    public static final NumberRangeParameter EMPTY = new NumberRangeParameter(RangeOperator.UNKNOWN, -1L);

    public static NumberRangeParameter valueOf(String valueRangeParam) {
        if (StringUtils.isBlank(valueRangeParam)) {
            return EMPTY;
        }

        var splitVal = valueRangeParam.split(":");
        return switch (splitVal.length) {
            case 1 -> new NumberRangeParameter(RangeOperator.EQ, getNumberValue(splitVal[0]));
            case 2 -> new NumberRangeParameter(RangeOperator.of(splitVal[0]), getNumberValue(splitVal[1]));
            default ->
                throw new IllegalArgumentException("Invalid range operator. Should have format 'operator:number'");
        };
    }

    private static long getNumberValue(String number) {
        var value = Long.parseLong(number);
        if (value < 0) {
            throw new IllegalArgumentException("Invalid range value");
        }

        return value;
    }

    public long getInclusiveValue() {
        if (operator == RangeOperator.GT) {
            return value + 1;
        } else if (operator == RangeOperator.LT) {
            return value - 1;
        } else {
            return value;
        }
    }
}
