// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

@UtilityClass
public class BytesDecoder {

    // Error(string)
    private static final Bytes ERROR_FUNCTION_SELECTOR = Bytes.fromHexString("0x08c379a0");
    private static final ABIType<Tuple> STRING_DECODER = TypeFactory.create("(string)");

    public static String maybeDecodeSolidityErrorStringToReadableMessage(final Bytes revertReason) {
        boolean isNullOrEmpty = revertReason == null || revertReason.isEmpty();

        if (isNullOrEmpty || revertReason.size() <= ERROR_FUNCTION_SELECTOR.size()) {
            return StringUtils.EMPTY;
        }

        if (isAbiEncodedErrorString(revertReason)) {
            final var encodedMessage = revertReason.slice(ERROR_FUNCTION_SELECTOR.size());
            final var tuple = STRING_DECODER.decode(encodedMessage.toArray());
            if (!tuple.isEmpty()) {
                return tuple.get(0);
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

    private static boolean isAbiEncodedErrorString(final Bytes revertReason) {
        return revertReason != null
                && revertReason.commonPrefixLength(ERROR_FUNCTION_SELECTOR) == ERROR_FUNCTION_SELECTOR.size();
    }
}
