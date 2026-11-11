// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX_CAPITAL;

import java.math.BigInteger;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HexUtils {

    /**
     * Parses a JSON number, or a string that is hexadecimal when {@code 0x}-prefixed and decimal otherwise.
     */
    public static long parseValue(final Object value) {
        return switch (value) {
            case Integer intValue -> intValue;
            case Long longValue -> longValue;
            case BigInteger bigInteger -> bigInteger.longValueExact();
            case String text when isHex(text.trim()) -> parseValue(text);
            // Signed, so a negative value reaches the request's range validation.
            case String text -> Long.parseLong(text.trim());
            default -> throw new IllegalArgumentException("expected a number or a hexadecimal/decimal string");
        };
    }

    /**
     * Parses an unsigned quantity. {@code 0x}-prefixed values are hex; otherwise decimal.
     */
    public static long parseValue(final String value) {
        final var trimmed = value.trim();
        if (isHex(trimmed)) {
            final var digits = trimmed.substring(2);
            return digits.isEmpty() ? 0L : Long.parseUnsignedLong(digits, 16);
        }
        return Long.parseUnsignedLong(trimmed, 10);
    }

    private static boolean isHex(final String value) {
        return value.startsWith(HEX_PREFIX) || value.startsWith(HEX_PREFIX_CAPITAL);
    }
}
