// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import java.math.BigInteger;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public record SlotRangeParameter(RangeOperator operator, String value) implements RangeParameter<String> {

    public static final SlotRangeParameter EMPTY = new SlotRangeParameter(RangeOperator.UNKNOWN, "");
    private static final String HEX_GROUP_NAME = "hex";
    private static final String OPERATOR_GROUP_NAME = "op";
    private static final Pattern SLOT_PATTERN =
            Pattern.compile("^(?:(?<op>eq|gt|gte|lt|lte):)?(?<hex>(?:0x)?[0-9a-fA-F]{1,64})$");

    public static SlotRangeParameter valueOf(String valueRangeParam) {
        if (StringUtils.isBlank(valueRangeParam)) {
            return EMPTY;
        }

        final var matcher = SLOT_PATTERN.matcher(valueRangeParam);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid storage slot. Must match pattern " + SLOT_PATTERN);
        }

        final var operatorGroup = matcher.group(OPERATOR_GROUP_NAME);
        final var hexGroup = matcher.group(HEX_GROUP_NAME);

        final var operator = (operatorGroup == null) ? RangeOperator.EQ : RangeOperator.of(operatorGroup);
        final var hex = normalizeHexKey(hexGroup);

        return new SlotRangeParameter(operator.toInclusive(), getInclusiveValue(operator, hex));
    }

    private static String normalizeHexKey(String hexValue) {
        final var hex = hexValue.toLowerCase().startsWith("0x") ? hexValue.substring(2) : hexValue;

        return StringUtils.leftPad(hex, 64, '0');
    }

    private static String getInclusiveValue(RangeOperator operator, String hexValue) {
        if (operator != RangeOperator.GT && operator != RangeOperator.LT) {
            return hexValue;
        }

        var value = new BigInteger(hexValue, 16);

        if (operator == RangeOperator.GT) {
            value = value.add(BigInteger.ONE);
        } else {
            value = value.subtract(BigInteger.ONE);
        }

        return value.toString(16);
    }
}
