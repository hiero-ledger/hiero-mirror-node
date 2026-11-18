// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import lombok.experimental.UtilityClass;
import org.hyperledger.besu.datatypes.Wei;
import org.jspecify.annotations.Nullable;

@UtilityClass
public class HexUtils {

    private static final String ZERO_HEX = "0x0";

    /**
     * Parses a hex string (with or without {@code 0x}/{@code 0X} prefix) into a long. Null, empty, prefix-only and
     * all-zero input parse as {@code 0}. A value at or above {@code 2^63} is rejected.
     */
    public static long convertHexStringToLong(final @Nullable String hex) {
        if (hex == null) {
            return 0L;
        }
        final int start = hex.startsWith("0x") || hex.startsWith("0X") ? 2 : 0;
        if (start == hex.length()) {
            return 0L;
        }
        final long value = Long.parseUnsignedLong(hex, start, hex.length(), 16);
        if (value < 0) {
            throw new NumberFormatException(hex + " produces a negative numeric value.");
        }
        return value;
    }

    public static String convertLongToHexString(final long number) {
        return "0x" + Long.toHexString(number);
    }

    public static String convertValueToHexString(final @Nullable Wei value) {
        if (value == null || value.isZero()) {
            return ZERO_HEX;
        }
        return value.toShortHexString();
    }
}
