// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import static org.hiero.mirror.restjava.util.BytesUtil.decrementByteArray;
import static org.hiero.mirror.restjava.util.BytesUtil.incrementByteArray;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.buf.HexUtils;
import org.hiero.mirror.common.util.DomainUtils;

public record SlotRangeParameter(RangeOperator operator, byte[] value) implements RangeParameter<byte[]> {

    public static final SlotRangeParameter EMPTY = new SlotRangeParameter(RangeOperator.UNKNOWN, new byte[0]);
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

    private static byte[] getInclusiveValue(RangeOperator operator, String hexValue) {
        byte[] bytes = HexUtils.fromHexString(hexValue);
        bytes = DomainUtils.leftPadBytes(bytes, 32);

        if (operator == RangeOperator.GT) {
            return incrementByteArray(bytes);
        } else if (operator == RangeOperator.LT) {
            return decrementByteArray(bytes);
        }
        return bytes;
    }
}
