// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import lombok.experimental.UtilityClass;
import org.hyperledger.besu.datatypes.Wei;
import org.jspecify.annotations.Nullable;

@UtilityClass
public class HexUtils {

    private static final String ZERO_HEX = "0x0";

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
