// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import java.math.BigInteger;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public record SlotRangeParameter(RangeOperator operator, String value) implements RangeParameter<String> {

    public static final SlotRangeParameter EMPTY = new SlotRangeParameter(RangeOperator.UNKNOWN, "");
    private static final Pattern SLOT_PATTERN = Pattern.compile("^((eq|gt|gte|lt|lte):)?(0x)?[0-9a-fA-F]{1,64}$");

    public static SlotRangeParameter valueOf(String valueRangeParam) {
        if (StringUtils.isBlank(valueRangeParam)) {
            return EMPTY;
        }

        if (!SLOT_PATTERN.matcher(valueRangeParam).matches()) {
            throw new IllegalArgumentException(
                    "Invalid key format: " + valueRangeParam + " must match <hex> or <op>:<64 or less char hex>");
        }

        var splitVal = valueRangeParam.split(":");

        RangeOperator operator;
        String hex;
        if (splitVal.length == 1) {
            operator = RangeOperator.EQ;
            hex = normalizeHexKey(splitVal[0]);
        } else {
            operator = RangeOperator.of(splitVal[0]);
            hex = normalizeHexKey(splitVal[1]);
        }

        return switch (operator) {
            case RangeOperator.EQ -> new SlotRangeParameter(RangeOperator.EQ, hex);
            case RangeOperator.GT, RangeOperator.GTE ->
                new SlotRangeParameter(RangeOperator.GTE, getInclusiveValue(operator, hex));
            case RangeOperator.LT, RangeOperator.LTE ->
                new SlotRangeParameter(RangeOperator.LTE, getInclusiveValue(operator, hex));
            default -> throw new IllegalStateException("Unsupported value for operator: " + operator);
        };
    }

    private static String normalizeHexKey(String hexValue) {
        final var hex = hexValue.replaceFirst("^(0x|0X)", "");
        return StringUtils.leftPad(hex, 64, '0');
    }

    private static String getInclusiveValue(RangeOperator operator, String hexValue) {
        var value = new BigInteger(hexValue, 16);

        if (operator == RangeOperator.GT) {
            value = value.add(BigInteger.ONE);
        } else if (operator == RangeOperator.LT) {
            value = value.subtract(BigInteger.ONE);
        }

        return value.toString(16);
    }
}
