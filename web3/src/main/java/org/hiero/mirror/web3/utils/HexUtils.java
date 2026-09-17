// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import java.util.HexFormat;
import lombok.experimental.UtilityClass;
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

    public static long parseHexLong(final @Nullable String hex) {
        if (hex == null || hex.isEmpty()) {
            return 0L;
        }
        final var value = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (value.isEmpty()) {
            return 0L;
        }
        return Long.parseUnsignedLong(value, 16);
    }
}
