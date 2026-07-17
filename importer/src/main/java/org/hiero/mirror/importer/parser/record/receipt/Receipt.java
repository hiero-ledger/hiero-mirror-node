// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import java.util.List;

/**
 * A minimal representation of an Ethereum transaction receipt used to compute a block's
 * receipts-trie root. Fields mirror the values the JSON-RPC relay uses when it builds the same trie, so the importer
 * produces a byte-identical {@code receipts_root}.
 *
 * @param transactionIndex position of the transaction within the block; also the trie key {@code RLP(index)}
 * @param type             EIP-2718 transaction type; 0 for legacy and non-EVM transactions, non-zero for typed
 *                         transactions whose receipt is prefixed with the type byte
 * @param success          whether the transaction succeeded; encoded as the receipt status (1 or empty) when the
 *                         receipt is not synthetic
 * @param synthetic        true for a transaction with logs but no contract result (an HTS transaction whose synthetic
 *                         contract result isn't persisted); its receipt's first field is 32 zero bytes verbatim
 *                         instead of the status, matching the relay's encoding of such transactions
 * @param gasUsed          gas used by the transaction; summed into the cumulative gas of the receipt
 * @param logsBloom        the 256-byte logs bloom (an empty array is treated as 256 zero bytes)
 * @param logs             the receipt logs in log-index order
 */
public record Receipt(
        int transactionIndex,
        int type,
        boolean success,
        boolean synthetic,
        long gasUsed,
        byte[] logsBloom,
        List<ReceiptLog> logs) {

    /**
     * A single receipt log.
     *
     * @param address the 20-byte contract EVM address
     * @param topics  the log topics, each left-padded to 32 bytes
     * @param data    the log data
     */
    public record ReceiptLog(byte[] address, List<byte[]> topics, byte[] data) {}
}
