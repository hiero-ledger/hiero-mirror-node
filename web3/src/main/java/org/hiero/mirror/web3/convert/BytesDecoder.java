// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class BytesDecoder {

    // Error(string)
    private static final String ERROR_FUNCTION_SELECTOR_HEX = "08c379a0";
    private static final int ERROR_FUNCTION_SELECTOR_LENGTH = 4;
    private static final ABIType<Tuple> STRING_DECODER = TypeFactory.create("(string)");

    public static String maybeDecodeSolidityErrorStringToReadableMessage(final String revertReasonHex) {
        if (revertReasonHex == null || revertReasonHex.isEmpty() || revertReasonHex.equals(HEX_PREFIX)) {
            return StringUtils.EMPTY;
        }

        String hex = revertReasonHex.startsWith(HEX_PREFIX) ? revertReasonHex.substring(2) : revertReasonHex;
        if (hex.length() <= ERROR_FUNCTION_SELECTOR_LENGTH * 2) {
            return StringUtils.EMPTY;
        }

        if (isAbiEncodedErrorString(revertReasonHex)) {
            String encodedMessageHex = hex.substring(ERROR_FUNCTION_SELECTOR_LENGTH * 2);
            try {
                byte[] encodedMessage = Hex.decodeHex(encodedMessageHex);
                final var tuple = STRING_DECODER.decode(encodedMessage);
                if (!tuple.isEmpty()) {
                    return tuple.get(0);
                }
            } catch (DecoderException e) {
                return StringUtils.EMPTY;
            }
        }
        return StringUtils.EMPTY;
    }

    /**
     * Returns an ABI-encoded revert reason as a hex string with "0x" prefix.
     * If the input is already ABI-encoded, returns it as-is.
     * Otherwise, encodes the input string as an Error(string) ABI call.
     *
     * @param revertReason the revert reason (plain text or hex string)
     * @return the ABI-encoded revert reason as hex string with "0x" prefix
     */
    public static String getAbiEncodedRevertReason(final String revertReason) {
        if (revertReason == null || revertReason.isEmpty()) {
            return HEX_PREFIX;
        }
        if (revertReason.startsWith(HEX_PREFIX)) {
            String hex = revertReason.substring(2);
            if (hex.isEmpty()) {
                return HEX_PREFIX;
            }
            if (isAbiEncodedErrorString(revertReason)) {
                return revertReason;
            }
            try {
                byte[] bytes = Hex.decodeHex(hex);
                if (bytes.length == 0) {
                    return HEX_PREFIX;
                }
                return getAbiEncodedRevertReasonFromBytes(bytes);
            } catch (DecoderException e) {
                return getAbiEncodedRevertReasonFromPlainText(revertReason);
            }
        }
        return getAbiEncodedRevertReasonFromPlainText(revertReason);
    }

    /**
     * Returns an ABI-encoded revert reason as a hex string with "0x" prefix.
     *
     * @param revertReasonBytes the revert reason as raw bytes
     * @return the ABI-encoded revert reason as hex string with "0x" prefix
     */
    public static String getAbiEncodedRevertReason(final byte[] revertReasonBytes) {
        if (revertReasonBytes == null || revertReasonBytes.length == 0) {
            return HEX_PREFIX;
        }
        String hexString = HEX_PREFIX + Hex.encodeHexString(revertReasonBytes);
        if (isAbiEncodedErrorString(hexString)) {
            return hexString;
        }
        return getAbiEncodedRevertReasonFromBytes(revertReasonBytes);
    }

    private static String getAbiEncodedRevertReasonFromBytes(final byte[] bytes) {
        String revertReasonPlain = new String(bytes);
        return getAbiEncodedRevertReasonFromPlainText(revertReasonPlain);
    }

    private static String getAbiEncodedRevertReasonFromPlainText(final String plainText) {
        byte[] encodedMessage = STRING_DECODER.encode(Tuple.from(plainText)).array();
        return HEX_PREFIX + ERROR_FUNCTION_SELECTOR_HEX + Hex.encodeHexString(encodedMessage);
    }

    private static boolean isAbiEncodedErrorString(final String revertReasonHex) {
        if (revertReasonHex == null || revertReasonHex.isEmpty()) {
            return false;
        }
        String hex = revertReasonHex.startsWith(HEX_PREFIX) ? revertReasonHex.substring(2) : revertReasonHex;
        return hex.toLowerCase().startsWith(ERROR_FUNCTION_SELECTOR_HEX);
    }
}
