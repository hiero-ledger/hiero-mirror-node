// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import jakarta.inject.Named;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;

/**
 * Computes the Ethereum block-header receipts-trie root from a block's receipts.
 */
@Named
public class ReceiptRootCalculator {

    private static final byte[] EMPTY_RECEIPTS_ROOT = new byte[32];
    private static final Bytes STATUS_SUCCESS = Bytes.of(1);
    private static final Bytes ZERO_BYTE = Bytes.of(0);

    /**
     * Calculates the 32-byte receipts-trie root
     */
    public byte[] calculate(final List<Receipt> receipts) {
        if (receipts.isEmpty()) {
            return EMPTY_RECEIPTS_ROOT.clone();
        }

        final var trie = new SimpleMerklePatriciaTrie<Bytes, Bytes>(Function.identity());
        long cumulativeGas = 0L;
        for (final var receipt : receipts.stream()
                .sorted(Comparator.comparingInt(Receipt::transactionIndex))
                .toList()) {
            cumulativeGas += receipt.gasUsed();
            trie.put(trieKey(receipt.transactionIndex()), encodeReceipt(receipt, cumulativeGas));
        }

        return trie.getRootHash().toArrayUnsafe();
    }

    private Bytes trieKey(final int transactionIndex) {
        final var out = new BytesValueRLPOutput();
        out.writeIntScalar(transactionIndex);
        return out.encoded();
    }

    private Bytes encodeReceipt(final Receipt receipt, final long cumulativeGas) {
        final var out = new BytesValueRLPOutput();
        out.startList();

        if (receipt.root() != null) {
            out.writeBytes(Bytes.wrap(receipt.root()));
        } else {
            out.writeBytes(receipt.success() ? STATUS_SUCCESS : Bytes.EMPTY);
        }

        if (cumulativeGas == 0L) {
            out.writeBytes(ZERO_BYTE);
        } else {
            out.writeLongScalar(cumulativeGas);
        }
        out.writeBytes(Bytes.wrap(normalizeBloom(receipt.logsBloom())));
        out.startList();
        for (final var log : receipt.logs()) {
            out.startList();
            out.writeBytes(Bytes.wrap(log.address()));
            out.startList();
            for (final var topic : log.topics()) {
                out.writeBytes(Bytes.wrap(topic));
            }
            out.endList();
            out.writeBytes(Bytes.wrap(log.data()));
            out.endList();
        }
        out.endList();
        out.endList();

        final var encoded = out.encoded();

        return receipt.type() == 0 ? encoded : Bytes.concatenate(Bytes.of(receipt.type()), encoded);
    }

    private byte[] normalizeBloom(final byte[] bloom) {
        return bloom != null && bloom.length == LogsBloomFilter.BYTE_SIZE ? bloom : new byte[LogsBloomFilter.BYTE_SIZE];
    }
}
