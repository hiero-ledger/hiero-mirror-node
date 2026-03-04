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
import org.apache.tuweni.bytes.Bytes;

@UtilityClass
public class BytesDecoder {

    // Error(string)
    private static final String ERROR_FUNCTION_SELECTOR_HEX = "0x08c379a0";
    private static final Bytes ERROR_FUNCTION_SELECTOR = Bytes.fromHexString(ERROR_FUNCTION_SELECTOR_HEX);
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

    public static Bytes getAbiEncodedRevertReason(final String revertReason) {
        if (revertReason == null || revertReason.isEmpty()) {
            return Bytes.EMPTY;
        }
        if (revertReason.startsWith(HEX_PREFIX)) {
            return getAbiEncodedRevertReason(Bytes.fromHexString(revertReason));
        }
        return getAbiEncodedRevertReason(Bytes.of(revertReason.getBytes()));
    }

    public static Bytes getAbiEncodedRevertReason(final Bytes revertReason) {
        if (revertReason == null || revertReason.isEmpty()) {
            return Bytes.EMPTY;
        }
        if (isAbiEncodedErrorString(revertReason)) {
            return revertReason;
        }
        String revertReasonPlain = new String(revertReason.toArray());
        return Bytes.concatenate(
                ERROR_FUNCTION_SELECTOR, Bytes.wrapByteBuffer(STRING_DECODER.encode(Tuple.from(revertReasonPlain))));
    }

    private static boolean isAbiEncodedErrorString(final String revertReasonHex) {
        if (revertReasonHex == null || revertReasonHex.isEmpty()) {
            return false;
        }
        String lowerHex = revertReasonHex.toLowerCase();
        return lowerHex.startsWith(ERROR_FUNCTION_SELECTOR_HEX);
    }

    private static boolean isAbiEncodedErrorString(final Bytes revertReason) {
        return revertReason != null
                && revertReason.commonPrefixLength(ERROR_FUNCTION_SELECTOR) == ERROR_FUNCTION_SELECTOR.size();
    }
}
