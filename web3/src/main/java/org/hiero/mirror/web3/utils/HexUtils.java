// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import java.util.HexFormat;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.web3.state.Utils;
import org.hyperledger.besu.datatypes.Wei;
import org.jspecify.annotations.Nullable;

@UtilityClass
public class HexUtils {

    private static final HexFormat HEX = HexFormat.of();
    private static final String ZERO_HEX = "0x0";

    public static String convertValueToHexString(final @Nullable Wei value) {
        if (value == null || value.isZero()) {
            return ZERO_HEX;
        }
        return value.toShortHexString();
    }

    public static String convertLongToHexString(final long number) {
        if (number == 0L) {
            return ZERO_HEX;
        }
        final var hex = HEX.toHexDigits(number);
        int start = 0;
        final var last = hex.length() - 1;
        while (start < last && hex.charAt(start) == '0') {
            start++;
        }
        return "0x" + hex.substring(start);
    }

    public static long convertHexStringToLong(final @Nullable String hex) {
        if (hex == null || hex.isEmpty()) {
            return 0L;
        }
        return Utils.hexStringToLong(hex);
    }

    /**
     * Parses an unsigned quantity that fits in a signed long. {@code 0x}-prefixed values are hex; otherwise decimal.
     * Null and empty hex parse as {@code 0}. A value at or above {@code 2^63} is rejected.
     */
    public static long parseValue(final String value) {
        final var trimmed = value.trim();
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            return convertHexStringToLong(trimmed);
        }
        final var parsed = Long.parseUnsignedLong(trimmed, 10);
        if (parsed < 0) {
            throw new NumberFormatException(trimmed + " produces a negative numeric value.");
        }
        return parsed;
    }
}
