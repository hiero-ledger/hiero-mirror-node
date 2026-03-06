// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;

@UtilityClass
public class BytesDecoder {

    // Error(string)
    private static final String ERROR_FUNCTION_SELECTOR = "0x08c379a0";
    private static final byte[] ERROR_SELECTOR = {(byte) 0x08, (byte) 0xc3, (byte) 0x79, (byte) 0xa0};
    private static final ABIType<Tuple> STRING_DECODER = TypeFactory.create("(string)");

    public static String maybeDecodeSolidityErrorStringToReadableMessage(final String revertReason) {
        boolean isNullOrEmpty = revertReason == null || revertReason.isEmpty();

        if (isNullOrEmpty || revertReason.length() <= ERROR_FUNCTION_SELECTOR.length()) {
            return StringUtils.EMPTY;
        }

        if (isAbiEncodedErrorString(revertReason)) {
            final var encodedMessageHex = revertReason.substring(ERROR_FUNCTION_SELECTOR.length());
            try {
                final var encodedMessage = Hex.decode(encodedMessageHex);
                final var tuple = STRING_DECODER.decode(encodedMessage);
                if (!tuple.isEmpty()) {
                    return tuple.get(0);
                }
            } catch (Exception e) {
                return StringUtils.EMPTY;
            }
        }
        return StringUtils.EMPTY;
    }

    public static String getAbiEncodedRevertReason(final String revertReason) {
        if (revertReason == null || revertReason.isEmpty() || HEX_PREFIX.equals(revertReason)) {
            return HEX_PREFIX;
        }
        if (isAbiEncodedErrorString(revertReason)) {
            return revertReason;
        }

        if (revertReason.startsWith(HEX_PREFIX)) {
            return ERROR_FUNCTION_SELECTOR + revertReason.substring(2);
        }

        final var encodedMessage =
                STRING_DECODER.encode(Tuple.from(revertReason)).array();
        return ERROR_FUNCTION_SELECTOR + Hex.toHexString(encodedMessage);
    }

    public static boolean startsWithErrorSelector(final byte[] bytes) {
        if (bytes.length < ERROR_SELECTOR.length) {
            return false;
        }
        for (int i = 0; i < ERROR_SELECTOR.length; i++) {
            if (bytes[i] != ERROR_SELECTOR[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAbiEncodedErrorString(final String revertReason) {
        return revertReason != null && revertReason.startsWith(ERROR_FUNCTION_SELECTOR);
    }

    /**
     * Checks whether a given hex string is valid.
     *
     * @param hexString the hex string (with or without "0x" prefix)
     * @throws IllegalArgumentException if the string contains invalid hex characters
     */
    public static void validateHexString(final String hexString) {
        if (hexString == null) {
            return;
        }

        String hex = hexString;
        if (hex.length() >= 2 && hex.charAt(0) == '0' && hex.charAt(1) == 'x') {
            hex = hex.substring(2);
        }

        int len = hex.length();
        if ((len & 0x01) != 0) {
            throw new IllegalArgumentException("Invalid odd-length hex binary representation");
        }

        for (int j = 0; j < len; j += 2) {
            int high = Character.digit(hex.charAt(j), 16);
            int low = Character.digit(hex.charAt(j + 1), 16);
            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("Invalid hex character in: " + hexString);
            }
        }
    }
}
