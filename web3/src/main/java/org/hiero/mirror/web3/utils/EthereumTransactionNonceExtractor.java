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
        final int firstByte = transactionBytes[0] & 0xFF;
        List<RLPItem> elements;
        final int nonceIndex;

        // Legacy Case: 0xc0-0xff is the RLP range for a List (Legacy)
        if (firstByte >= 0xc0) {
            final var txnList = decoder.next();
            elements = txnList.asRLPList().elements();
            nonceIndex = 0; // Nonce is the 1st field in legacy txn: [nonce, gasPrice...]
        } else {
            // Typed Case: The first item is the Type Byte (0x01, 0x02, 0x04)
            decoder.next(); // Consume and skip the Type Byte
            final var payload = decoder.next();
            if (!payload.isList()) {
                return null;
            }
            elements = payload.asRLPList().elements();
            nonceIndex = 1; // Nonce is the 2nd field in typed txn: [chainId, nonce, maxFee...]
        }
        if (elements.size() <= nonceIndex) {
            return null;
        }
        return elements.get(nonceIndex).asLong();
    }
}
