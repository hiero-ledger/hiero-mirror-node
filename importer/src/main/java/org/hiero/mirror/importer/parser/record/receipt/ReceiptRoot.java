// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
 * Accumulates one block's contract results and logs as they stream by and computes the Ethereum block-header
 * receipts-trie root, byte-identical to the root the JSON-RPC relay computes for the same block. Additions may arrive
 * in any order and are grouped by consensus timestamp; receipts are encoded from the domain objects only when the
 * root is computed, so late mutations (e.g. synthetic log topics resolved at the end of the block) are reflected.
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

    private final Map<Long, TransactionData> transactionsByTimestamp = new LinkedHashMap<>();
    private final Set<Long> contractIds = new LinkedHashSet<>();

    /**
     * Adds a transaction's contract result. Child (internal) contract results must be filtered out by the caller.
     */
    public void add(final ContractResult contractResult) {
        transaction(contractResult.getConsensusTimestamp()).contractResult = contractResult;
    }

    /**
     * Adds a contract log to its transaction's receipt; a transaction with logs but no contract result becomes a
     * synthetic receipt.
     */
    public void add(final ContractLog contractLog) {
        transaction(contractLog.getConsensusTimestamp()).logs.put(contractLog.getIndex(), contractLog);
        if (!EntityId.isEmpty(contractLog.getContractId())) {
            contractIds.add(contractLog.getContractId().getId());
        }
    }

    /**
     * @return the distinct contract ids of the added logs, for EVM address alias resolution
     */
    public Set<Long> getContractIds() {
        return Collections.unmodifiableSet(contractIds);
    }

    /**
     * @param typeByConsensusTimestamp the EIP-2718 ethereum transaction types by consensus timestamp; transactions
     *                                 absent from the map are encoded as legacy (type 0) receipts
     * @param evmAddressById           the EVM address aliases of the contracts emitting the block's logs; contracts
     *                                 absent from the map use their long-zero address
     * @return the 32-byte receipts-trie root; 32 zero bytes when nothing was added
     */
    public byte[] getRootHash(
            final Map<Long, Integer> typeByConsensusTimestamp, final Map<Long, byte[]> evmAddressById) {
        if (transactionsByTimestamp.isEmpty()) {
            return EMPTY_RECEIPTS_ROOT;
        }

        final var transactions = new ArrayList<>(transactionsByTimestamp.values());
        transactions.sort(Comparator.comparingInt(TransactionData::transactionIndex));

        final var trie = new SimpleMerklePatriciaTrie<Bytes, Bytes>(Function.identity());
        long cumulativeGas = 0L;
        for (final var transaction : transactions) {
            final var contractResult = transaction.contractResult;
            if (contractResult != null && contractResult.getGasUsed() != null) {
                cumulativeGas += contractResult.getGasUsed();
            }
            final var type = transaction.contractResult != null
                    ? typeByConsensusTimestamp.get(transaction.consensusTimestamp)
                    : null;
            trie.put(
                    trieKey(transaction.transactionIndex()),
                    encodeReceipt(transaction, type, cumulativeGas, evmAddressById));
        }

        return trie.getRootHash().toArrayUnsafe();
    }

    private TransactionData transaction(final long consensusTimestamp) {
        return transactionsByTimestamp.computeIfAbsent(consensusTimestamp, TransactionData::new);
    }

    private Bytes trieKey(final int transactionIndex) {
        final var out = new BytesValueRLPOutput();
        out.writeIntScalar(transactionIndex);
        return out.encoded();
    }

    private Bytes encodeReceipt(
            final TransactionData transaction,
            @Nullable final Integer type,
            final long cumulativeGas,
            final Map<Long, byte[]> evmAddressById) {
        final var contractResult = transaction.contractResult;
        final var out = new BytesValueRLPOutput();
        out.startList();

        if (contractResult == null) {
            out.writeBytes(SYNTHETIC_FIRST_FIELD);
        } else {
            final var success = SUCCESS_TRANSACTION_RESULTS.contains(contractResult.getTransactionResult());
            out.writeBytes(success ? STATUS_SUCCESS : Bytes.EMPTY);
        }

        out.writeLongScalar(cumulativeGas);
        out.writeBytes(Bytes.wrap(normalizeBloom(bloom(transaction, evmAddressById))));
        out.startList();
        for (final var contractLog : transaction.logs.values()) {
            out.startList();
            out.writeBytes(Bytes.wrap(logAddress(contractLog, evmAddressById)));
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

        return type == null || type == 0 ? encoded : Bytes.concatenate(Bytes.of(type), encoded);
    }

    private byte[] bloom(final TransactionData transaction, final Map<Long, byte[]> evmAddressById) {
        if (transaction.contractResult != null) {
            return transaction.contractResult.getBloom();
        }

        var bloom = new byte[LogsBloomFilter.BYTE_SIZE];
        for (final var contractLog : transaction.logs.values()) {
            final var filter = new LogsBloomFilter();
            filter.insertAddress(logAddress(contractLog, evmAddressById));
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

    private byte[] logAddress(final ContractLog contractLog, final Map<Long, byte[]> evmAddressById) {
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
     * A single transaction's data, grouped by consensus timestamp as additions stream by.
     */
    private static final class TransactionData {

        private final long consensusTimestamp;
        private final TreeMap<Integer, ContractLog> logs = new TreeMap<>();

        @Nullable
        private ContractResult contractResult;

        private TransactionData(final long consensusTimestamp) {
            this.consensusTimestamp = consensusTimestamp;
        }

        private int transactionIndex() {
            if (contractResult != null) {
                return contractResult.getTransactionIndex() != null ? contractResult.getTransactionIndex() : 0;
            }

            final var firstLog = logs.firstEntry().getValue();
            return firstLog.getTransactionIndex() != null ? firstLog.getTransactionIndex() : 0;
        }
    }
}
