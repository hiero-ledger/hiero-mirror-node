// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.jspecify.annotations.Nullable;

/**
 * Accumulates one block's transactions and computes the Ethereum block-header receipts-trie root. The single entry
 * point for receipts root computation; the receipt encoding and trie internals stay behind this class.
 */
public final class ReceiptRoot {

    private static final ReceiptRootCalculator CALCULATOR = new ReceiptRootCalculator();

    private final Map<Long, byte[]> evmAddressById;
    private final List<Receipt> receipts = new ArrayList<>();

    /**
     * @param evmAddressById the EVM address aliases of the contracts emitting the block's logs; contracts absent from
     *                       the map use their long-zero address
     */
    public ReceiptRoot(final Map<Long, byte[]> evmAddressById) {
        this.evmAddressById = evmAddressById;
    }

    /**
     * Groups the block's contract results and logs by transaction and puts each into a new receipts trie.
     */
    public static ReceiptRoot of(
            final Collection<ContractResult> contractResults,
            final Collection<ContractLog> contractLogs,
            final Map<Long, Integer> typeByConsensusTimestamp,
            final Map<Long, byte[]> evmAddressById) {
        final var timestamps = LinkedHashSet.<Long>newLinkedHashSet(contractResults.size() + contractLogs.size());

        final var resultsByTimestamp = HashMap.<Long, ContractResult>newHashMap(contractResults.size());
        for (final var contractResult : contractResults) {
            resultsByTimestamp.put(contractResult.getConsensusTimestamp(), contractResult);
            timestamps.add(contractResult.getConsensusTimestamp());
        }

        final var logsByTimestamp = new HashMap<Long, TreeMap<Integer, ContractLog>>();
        for (final var contractLog : contractLogs) {
            logsByTimestamp
                    .computeIfAbsent(contractLog.getConsensusTimestamp(), k -> new TreeMap<>())
                    .put(contractLog.getIndex(), contractLog);
            timestamps.add(contractLog.getConsensusTimestamp());
        }

        final var receiptRoot = new ReceiptRoot(evmAddressById);
        for (final var timestamp : timestamps) {
            final var logs = logsByTimestamp.get(timestamp);
            receiptRoot.put(
                    resultsByTimestamp.get(timestamp),
                    logs != null ? logs.values() : List.of(),
                    typeByConsensusTimestamp.get(timestamp));
        }

        return receiptRoot;
    }

    /**
     * Adds a single transaction's receipt to the trie.
     *
     * @param contractResult the transaction's contract result; null for a synthetic receipt built from logs only
     * @param contractLogs   the transaction's logs in log index order
     * @param ethereumType   the EIP-2718 ethereum transaction type; null for non-ethereum transactions
     */
    public void put(
            @Nullable final ContractResult contractResult,
            final Collection<ContractLog> contractLogs,
            @Nullable final Integer ethereumType) {
        receipts.add(ReceiptAssembler.toReceipt(contractResult, contractLogs, ethereumType, evmAddressById));
    }

    /**
     * @return the 32-byte receipts-trie root; 32 zero bytes when no receipts were put
     */
    public byte[] getRootHash() {
        return CALCULATOR.calculate(receipts);
    }
}
