// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

@UtilityClass
public class EthereumTransactionNonceExtractor {

    /**
     * Extracts the nonce from raw Ethereum transaction bytes.
     *
     * @param transactionBytes RLP-encoded Ethereum transaction ( Legacy, EIP-2930, EIP-1559, or EIP-7702)
     * @return the transaction nonce, or null if the bytes cannot be parsed
     */
    @Nullable
    public static Long extractNonce(byte[] transactionBytes) {
        if (transactionBytes == null || transactionBytes.length < 2) {
            return null;
        }

        final var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(transactionBytes);
        final var first = decoder.next();
        List<RLPItem> elements;
        final int nonceIndex;
        if (first.isList()) { // 0xc0-0xff indicates a list, indicating legacy txn, i.e txn without a type byte
            elements = first.asRLPList().elements();
            nonceIndex = 0; // in legacy txns the fist element of the list is the transaction nonce
        } else {
            final var payload = decoder.next();
            if (!payload.isList()) {
                return null;
            }
            elements = payload.asRLPList().elements();
            nonceIndex = 1; // in non-legacy txns the first element is the chainId, the second is the nonce
        }
        if (elements.size() <= nonceIndex) {
            return null;
        }
        return elements.get(nonceIndex).asLong();
    }
}
