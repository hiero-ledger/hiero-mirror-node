// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import static org.hiero.mirror.restjava.util.BytesUtil.decrementByteArray;
import static org.hiero.mirror.restjava.util.BytesUtil.incrementByteArray;

import java.util.Arrays;
import java.util.regex.Pattern;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.restjava.common.RangeOperator;

public record SlotRangeParameter(RangeOperator operator, byte[] value) implements RangeParameter<byte[]> {

    public static final SlotRangeParameter EMPTY = new SlotRangeParameter(RangeOperator.UNKNOWN, new byte[0]);
    public static final byte[] MAX_KEY_BYTES = new byte[SlotRangeParameter.KEY_BYTE_LENGTH];
    public static final byte[] MIN_KEY_BYTES = new byte[SlotRangeParameter.KEY_BYTE_LENGTH]; // A 32-byte array of 0x00
    private static final String HEX_GROUP_NAME = "hex";
    private static final int KEY_BYTE_LENGTH = 32;
    private static final String OPERATOR_GROUP_NAME = "op";
    private static final Pattern SLOT_PATTERN =
            Pattern.compile("^(?:(?<op>eq|gt|gte|lt|lte):)?(?:0x)?(?<hex>[0-9a-fA-F]{1,64})$");

    static {
        Arrays.fill(MAX_KEY_BYTES, (byte) 0xFF); // A 32-byte array of 0xFF
    }

    public static SlotRangeParameter valueOf(String valueRangeParam) throws DecoderException {
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
        final var hex = StringUtils.leftPad(hexGroup, 64, '0');
        final byte[] bytes = Hex.decodeHex(hex);

        // Nothing is greater than the max slot or less than the min slot. Reject instead of wrapping around.
        if ((operator == RangeOperator.GT && Arrays.equals(MAX_KEY_BYTES, bytes))
                || (operator == RangeOperator.LT && Arrays.equals(MIN_KEY_BYTES, bytes))) {
            throw new IllegalArgumentException("Invalid storage slot. Value is out of range");
        }

        return new SlotRangeParameter(operator.toInclusive(), getInclusiveValue(operator, bytes));
    }

    private static byte[] getInclusiveValue(RangeOperator operator, byte[] bytes) {
        if (operator == RangeOperator.GT) {
            return incrementByteArray(bytes);
        } else if (operator == RangeOperator.LT) {
            return decrementByteArray(bytes);
        }
        return bytes;
    }
}
