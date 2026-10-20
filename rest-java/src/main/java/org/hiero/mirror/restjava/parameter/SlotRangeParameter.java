// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import static org.hiero.mirror.restjava.util.BytesUtil.decrementByteArray;
import static org.hiero.mirror.restjava.util.BytesUtil.incrementByteArray;

import java.util.regex.Pattern;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.restjava.common.RangeOperator;

public record SlotRangeParameter(RangeOperator operator, byte[] value) implements RangeParameter<byte[]> {

    public static final SlotRangeParameter EMPTY = new SlotRangeParameter(RangeOperator.UNKNOWN, new byte[0]);
    private static final String HEX_GROUP_NAME = "hex";
    private static final String OPERATOR_GROUP_NAME = "op";
    private static final Pattern SLOT_PATTERN =
            Pattern.compile("^(?:(?<op>eq|gt|gte|lt|lte):)?(?:0x)?(?<hex>[0-9a-fA-F]{1,64})$");

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

        // Exclusive bounds past the 32-byte domain edge are well-formed but unsatisfiable
        // (nothing is > 0xff..ff, nothing is < 0x00..00). Keep them as exclusive GT/LT of the
        // edge value so callers can yield an empty page — do NOT wrap (which would match every
        // slot) and do NOT 400 (which breaks pagination next links that land on the edge).
        if (operator == RangeOperator.GT && isAll(bytes, (byte) 0xff)) {
            return new SlotRangeParameter(RangeOperator.GT, bytes);
        }
        if (operator == RangeOperator.LT && isAll(bytes, (byte) 0)) {
            return new SlotRangeParameter(RangeOperator.LT, bytes);
        }

        return new SlotRangeParameter(operator.toInclusive(), getInclusiveValue(operator, bytes));
    }

    /**
     * True when this parameter is an exclusive bound past the end of the slot domain, so no key can
     * match. Callers should treat it as an empty result set.
     */
    public boolean isUnsatisfiable() {
        if (operator == RangeOperator.GT) {
            return value.length > 0 && isAll(value, (byte) 0xff);
        }
        if (operator == RangeOperator.LT) {
            return value.length > 0 && isAll(value, (byte) 0);
        }
        return false;
    }

    private static byte[] getInclusiveValue(RangeOperator operator, byte[] bytes) {
        if (operator == RangeOperator.GT) {
            return incrementByteArray(bytes);
        } else if (operator == RangeOperator.LT) {
            return decrementByteArray(bytes);
        }
        return bytes;
    }

    private static boolean isAll(byte[] bytes, byte value) {
        for (byte b : bytes) {
            if (b != value) {
                return false;
            }
        }
        return true;
    }
}
