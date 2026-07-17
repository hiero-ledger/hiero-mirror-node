// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.util.Utility;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;
import org.jspecify.annotations.Nullable;

/**
 * Accumulates one block's transactions and computes the Ethereum block-header receipts-trie root, byte-identical to
 * the root the JSON-RPC relay computes for the same block. The single entry point for receipts root computation;
 * receipts are encoded directly from the domain objects.
 */
public final class ReceiptRoot {

    private static final byte[] EMPTY_RECEIPTS_ROOT = new byte[32];
    private static final Bytes STATUS_SUCCESS = Bytes.of(1);
    // The relay encodes the receipt of a transaction without a contract result (an HTS transaction whose synthetic
    // contract result isn't persisted) with 32 zero bytes in place of the EIP-658 status
    private static final Bytes SYNTHETIC_FIRST_FIELD = Bytes.wrap(new byte[32]);
    private static final int ADDRESS_LENGTH = 20;
    private static final int WORD_LENGTH = 32;
    private static final Set<Integer> SUCCESS_TRANSACTION_RESULTS = Set.of(
            ResponseCodeEnum.SUCCESS_VALUE,
            ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED_VALUE,
            ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION_VALUE);

    private final Map<Long, byte[]> evmAddressById;
    private final List<TransactionData> transactions = new ArrayList<>();

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
     * Adds a single transaction to the trie.
     *
     * @param contractResult the transaction's contract result; null for a synthetic receipt built from logs only
     * @param contractLogs   the transaction's logs in log index order
     * @param ethereumType   the EIP-2718 ethereum transaction type; null for non-ethereum transactions
     */
    public void put(
            @Nullable final ContractResult contractResult,
            final Collection<ContractLog> contractLogs,
            @Nullable final Integer ethereumType) {
        transactions.add(new TransactionData(
                transactionIndex(contractResult, contractLogs), contractResult, contractLogs, ethereumType));
    }

    /**
     * @return the 32-byte receipts-trie root; 32 zero bytes when no transactions were put
     */
    public byte[] getRootHash() {
        if (transactions.isEmpty()) {
            return EMPTY_RECEIPTS_ROOT;
        }

        final var trie = new SimpleMerklePatriciaTrie<Bytes, Bytes>(Function.identity());
        long cumulativeGas = 0L;
        for (final var transaction : transactions.stream()
                .sorted(Comparator.comparingInt(TransactionData::transactionIndex))
                .toList()) {
            final var contractResult = transaction.contractResult();
            if (contractResult != null && contractResult.getGasUsed() != null) {
                cumulativeGas += contractResult.getGasUsed();
            }
            trie.put(trieKey(transaction.transactionIndex()), encodeReceipt(transaction, cumulativeGas));
        }

        return trie.getRootHash().toArrayUnsafe();
    }

    private static int transactionIndex(
            @Nullable final ContractResult contractResult, final Collection<ContractLog> contractLogs) {
        if (contractResult != null) {
            return contractResult.getTransactionIndex() != null ? contractResult.getTransactionIndex() : 0;
        }

        final var firstLog = contractLogs.iterator().next();
        return firstLog.getTransactionIndex() != null ? firstLog.getTransactionIndex() : 0;
    }

    private Bytes trieKey(final int transactionIndex) {
        final var out = new BytesValueRLPOutput();
        out.writeIntScalar(transactionIndex);
        return out.encoded();
    }

    private Bytes encodeReceipt(final TransactionData transaction, final long cumulativeGas) {
        final var contractResult = transaction.contractResult();
        final var out = new BytesValueRLPOutput();
        out.startList();

        if (contractResult == null) {
            out.writeBytes(SYNTHETIC_FIRST_FIELD);
        } else {
            final var success = SUCCESS_TRANSACTION_RESULTS.contains(contractResult.getTransactionResult());
            out.writeBytes(success ? STATUS_SUCCESS : Bytes.EMPTY);
        }

        out.writeLongScalar(cumulativeGas);
        out.writeBytes(Bytes.wrap(normalizeBloom(bloom(transaction))));
        out.startList();
        for (final var contractLog : transaction.contractLogs()) {
            out.startList();
            out.writeBytes(Bytes.wrap(logAddress(contractLog)));
            out.startList();
            for (final var topic : topics(contractLog)) {
                out.writeBytes(Bytes.wrap(topic));
            }
            out.endList();
            out.writeBytes(Bytes.wrap(data(contractLog.getData())));
            out.endList();
        }
        out.endList();
        out.endList();

        final var encoded = out.encoded();
        final var type = contractResult != null && transaction.ethereumType() != null ? transaction.ethereumType() : 0;

        return type == 0 ? encoded : Bytes.concatenate(Bytes.of(type), encoded);
    }

    private byte[] bloom(final TransactionData transaction) {
        if (transaction.contractResult() != null) {
            return transaction.contractResult().getBloom();
        }

        var bloom = new byte[LogsBloomFilter.BYTE_SIZE];
        for (final var contractLog : transaction.contractLogs()) {
            final var filter = new LogsBloomFilter();
            filter.insertAddress(logAddress(contractLog));
            for (final var topic : topics(contractLog)) {
                filter.insertTopic(topic);
            }
            bloom = LogsBloomFilter.or(filter.toArrayUnsafe(), bloom);
        }

        return bloom;
    }

    /**
     *  Ethereum receipt's logsBloom is 256 bytes by definition. Since the goal is to reproduce byte-for-byte the
     *  Ethereum block header's receipts root, if the input bloom is null or not 256 bytes, return a
     *  new byte array of 256 byte zero array.
     */
    private byte[] normalizeBloom(final byte[] bloom) {
        // covers the case with receipt with no bloom(null) and no-logs(length=0) bloom respectively
        if (bloom == null || bloom.length == 0) {
            return new byte[LogsBloomFilter.BYTE_SIZE];
        }
        if (bloom.length != LogsBloomFilter.BYTE_SIZE) {
            Utility.handleRecoverableError("Unexpected bloom length {}", bloom.length);
            return new byte[LogsBloomFilter.BYTE_SIZE];
        }
        return bloom;
    }

    private byte[] logAddress(final ContractLog contractLog) {
        final var contractId = contractLog.getContractId();
        if (EntityId.isEmpty(contractId)) {
            return new byte[ADDRESS_LENGTH];
        }

        final var evmAddress = evmAddressById.get(contractId.getId());
        return DomainUtils.leftPadBytes(
                evmAddress != null ? evmAddress : DomainUtils.toEvmAddress(contractId), ADDRESS_LENGTH);
    }

    private List<byte[]> topics(final ContractLog contractLog) {
        final var topics = new ArrayList<byte[]>(4);
        for (final var topic : new byte[][] {
            contractLog.getTopic0(), contractLog.getTopic1(), contractLog.getTopic2(), contractLog.getTopic3()
        }) {
            if (topic != null) {
                topics.add(DomainUtils.leftPadBytes(topic, WORD_LENGTH));
            }
        }

        return topics;
    }

    private byte[] data(final byte[] data) {
        if (ArrayUtils.isEmpty(data)) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        return data.length < WORD_LENGTH ? DomainUtils.leftPadBytes(data, WORD_LENGTH) : data;
    }

    /**
     * A single transaction buffered until {@link #getRootHash()}; holds references to the domain objects so receipts
     * can be encoded from them directly once the transaction order and cumulative gas are known.
     */
    private record TransactionData(
            int transactionIndex,
            @Nullable ContractResult contractResult,
            Collection<ContractLog> contractLogs,
            @Nullable Integer ethereumType) {}
}
