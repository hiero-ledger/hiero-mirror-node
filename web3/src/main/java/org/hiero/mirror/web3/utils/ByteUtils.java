// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.codec.binary.Hex;

@UtilityClass
public class ByteUtils {

    private static final String HEX_PREFIX = "0x";
    private static final int WORD_SIZE_BYTES = 32;
    private static final int WORD_SIZE_HEX_CHARS = WORD_SIZE_BYTES * 2;

    /**
     * Converts a byte array to a hex string with "0x" prefix, zero-padded to 32 bytes (64 hex characters).
     *
     * @param bytes the byte array to convert
     * @return hex string with "0x" prefix and zero-padded to 64 hex characters, or null if input is null
     */
    public static String wrapToWordSize(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        final var hex = Hex.encodeHexString(bytes);
        if (hex.length() >= WORD_SIZE_HEX_CHARS) {
            return HEX_PREFIX + hex;
        }

        final var padded = "0".repeat(WORD_SIZE_HEX_CHARS - hex.length()) + hex;
        return HEX_PREFIX + padded;
    }
}
