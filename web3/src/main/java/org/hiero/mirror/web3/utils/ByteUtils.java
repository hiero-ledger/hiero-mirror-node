// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import java.util.HexFormat;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

@UtilityClass
public class ByteUtils {

    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final int WORD_SIZE_BYTES = 32;
    public static final int WORD_SIZE_HEX_CHARS = WORD_SIZE_BYTES * 2;
    public static final String ZERO_WORD = HEX_PREFIX + "0".repeat(WORD_SIZE_HEX_CHARS);

    /**
     * Converts a byte array to a hex string with "0x" prefix, left-padded or right-truncated to 32 bytes
     * (64 hex characters).
     *
     * @param bytes the byte array to convert
     * @return hex string with "0x" prefix and exactly 64 hex characters, or null if input is null
     */
    public static @Nullable String wrapToWordSize(final byte @Nullable [] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length == 0) {
            return ZERO_WORD;
        }
        if (bytes.length == WORD_SIZE_BYTES) {
            return HEX_PREFIX + HEX_FORMAT.formatHex(bytes);
        }
        if (bytes.length < WORD_SIZE_BYTES) {
            return HEX_PREFIX + "0".repeat((WORD_SIZE_BYTES - bytes.length) * 2) + HEX_FORMAT.formatHex(bytes);
        }
        return HEX_PREFIX + HEX_FORMAT.formatHex(bytes, bytes.length - WORD_SIZE_BYTES, bytes.length);
    }
}
