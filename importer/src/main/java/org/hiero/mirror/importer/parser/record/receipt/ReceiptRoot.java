// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.util.Utility;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;
import org.jspecify.annotations.Nullable;

/**
 * Computes the block-header receipts-trie root for a record file. The trie is kept up to date as each top-level EVM
 * transaction is {@link #put(ContractResult, Collection, EthereumTransaction) put}. All state lives in this instance,
 * which is created per record file by the {@code RecordItemAggregator}.
 *
 * <p>Because cumulative gas is accumulated incrementally, transactions must be put in ascending transaction-index order.
 * EVM addresses (a log's contract address and a synthetic log's resolved topics) come from {@code evmAddressById}, which
 * the caller populates incrementally as it processes each transaction, so the computation does not depend on the
 * synthetic log topics/blooms being finalized later in {@code SyntheticLogListener.onEnd()}.
 */
public final class ReceiptRoot {

    private static final byte[] EMPTY_RECEIPTS_ROOT = new byte[32];
    private static final Bytes EMPTY_BLOOM = Bytes.wrap(new byte[LogsBloomFilter.BYTE_SIZE]);
    private static final Bytes STATUS_SUCCESS = Bytes.of(1);
    // The relay encodes the receipt of a transaction without a contract result (an HTS transaction whose synthetic
    // contract result isn't persisted) with 32 zero bytes in place of the EIP-658 status
    private static final Bytes SYNTHETIC_FIRST_FIELD = Bytes.wrap(new byte[32]);
    private static final int ADDRESS_LENGTH = 20;
    private static final byte[] EMPTY_ADDRESS = new byte[ADDRESS_LENGTH];
    private static final int WORD_LENGTH = 32;
    private static final Set<Integer> SUCCESS_TRANSACTION_RESULTS = Set.of(
            ResponseCodeEnum.SUCCESS_VALUE,
            ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED_VALUE,
            ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION_VALUE);

    // Trimmed EVM address aliases by entity id; entities absent from the map fall back to their long-zero address
    private final Map<Long, byte[]> evmAddressById;

    private final SimpleMerklePatriciaTrie<Bytes, Bytes> trie = new SimpleMerklePatriciaTrie<>(Function.identity());
    private long cumulativeGas = 0L;
    private boolean empty = true;

    public ReceiptRoot(final Map<Long, byte[]> evmAddressById) {
        this.evmAddressById = evmAddressById;
    }

    /**
     * Adds a top-level EVM transaction's receipt to the trie. Child (internal) contract results and their logs must be
     * excluded by the caller. A transaction with logs but no contract result is encoded as a synthetic receipt. Must be
     * called in ascending transaction-index order so cumulative gas accumulates correctly.
     *
     * @param contractResult      the transaction's contract result, or {@code null} for a synthetic-only receipt
     * @param contractLogs        the transaction's logs; encoded in ascending log index order
     * @param ethereumTransaction the transaction's ethereum transaction, or {@code null}; supplies the EIP-2718 type
     */
    public void put(
            @Nullable final ContractResult contractResult,
            final Collection<ContractLog> contractLogs,
            @Nullable final EthereumTransaction ethereumTransaction) {
        final var transactionIndex = transactionIndex(contractResult, contractLogs);
        // A null transaction index means the transaction holds no EVM transaction index slot (e.g. a WRONG_NONCE
        // ethereum transaction that never entered EVM execution) and is not part of the receipts trie
        if (transactionIndex == null) {
            return;
        }

        if (contractResult != null && contractResult.getGasUsed() != null) {
            cumulativeGas += contractResult.getGasUsed();
        }

        final var logs = new ArrayList<>(contractLogs);
        logs.sort(Comparator.comparingInt(ContractLog::getIndex));
        final var type = ethereumTransaction != null ? ethereumTransaction.getType() : null;
        trie.put(trieKey(transactionIndex), encodeReceipt(contractResult, logs, type, cumulativeGas));
        empty = false;
    }

    /**
     * @return the 32-byte receipts-trie root; 32 zero bytes when nothing was added
     */
    public byte[] getRootHash() {
        return empty ? EMPTY_RECEIPTS_ROOT : trie.getRootHash().toArrayUnsafe();
    }

    @Nullable
    private Integer transactionIndex(
            @Nullable final ContractResult contractResult, final Collection<ContractLog> contractLogs) {
        if (contractResult != null) {
            return contractResult.getTransactionIndex();
        }

        return contractLogs.stream()
                .findFirst()
                .map(ContractLog::getTransactionIndex)
                .orElse(null);
    }

    private Bytes trieKey(final int transactionIndex) {
        final var out = new BytesValueRLPOutput();
        out.writeIntScalar(transactionIndex);
        return out.encoded();
    }

    private Bytes encodeReceipt(
            @Nullable final ContractResult contractResult,
            final List<ContractLog> contractLogs,
            @Nullable final Integer type,
            final long cumulativeGas) {
        final var out = new BytesValueRLPOutput();
        out.startList();

        if (contractResult == null) {
            out.writeBytes(SYNTHETIC_FIRST_FIELD);
        } else {
            final var success = SUCCESS_TRANSACTION_RESULTS.contains(contractResult.getTransactionResult());
            out.writeBytes(success ? STATUS_SUCCESS : Bytes.EMPTY);
        }

        out.writeLongScalar(cumulativeGas);
        out.writeBytes(normalizeBloom(bloom(contractResult, contractLogs)));
        out.startList();
        for (final var contractLog : contractLogs) {
            out.startList();
            out.writeBytes(Bytes.wrap(logAddress(contractLog)));
            out.startList();
            writeTopic(out, contractLog.getTopic0());
            writeTopic(out, resolveTopic(contractLog, contractLog.getTopic1()));
            writeTopic(out, resolveTopic(contractLog, contractLog.getTopic2()));
            writeTopic(out, contractLog.getTopic3());
            out.endList();
            out.writeBytes(Bytes.wrap(data(contractLog.getData())));
            out.endList();
        }
        out.endList();
        out.endList();

        final var encoded = out.encoded();

        return type == null || type == 0 ? encoded : Bytes.concatenate(Bytes.of(type), encoded);
    }

    private byte[] bloom(@Nullable final ContractResult contractResult, final List<ContractLog> contractLogs) {
        if (contractResult != null) {
            return contractResult.getBloom();
        }

        var bloom = new byte[LogsBloomFilter.BYTE_SIZE];
        for (final var contractLog : contractLogs) {
            final var filter = new LogsBloomFilter();
            filter.insertAddress(logAddress(contractLog));
            filter.insertTopic(contractLog.getTopic0());
            filter.insertTopic(resolveTopic(contractLog, contractLog.getTopic1()));
            filter.insertTopic(resolveTopic(contractLog, contractLog.getTopic2()));
            filter.insertTopic(contractLog.getTopic3());
            bloom = LogsBloomFilter.or(filter.toArrayUnsafe(), bloom);
        }

        return bloom;
    }

    /**
     *  Ethereum receipt's logsBloom is 256 bytes by definition. Since the goal is to reproduce byte-for-byte the
     *  Ethereum block header's receipts root, if the input bloom is null or not 256 bytes, return a
     *  256 byte zero bloom.
     */
    private Bytes normalizeBloom(final byte[] bloom) {
        // covers the case with receipt with no bloom(null) and no-logs(length=0) bloom respectively
        if (bloom == null || bloom.length == 0) {
            return EMPTY_BLOOM;
        }
        if (bloom.length != LogsBloomFilter.BYTE_SIZE) {
            Utility.handleRecoverableError("Unexpected bloom length {}", bloom.length);
            return EMPTY_BLOOM;
        }
        return Bytes.wrap(bloom);
    }

    private byte[] logAddress(final ContractLog contractLog) {
        final var contractId = contractLog.getContractId();
        if (EntityId.isEmpty(contractId)) {
            return EMPTY_ADDRESS;
        }

        final var evmAddress = resolveEvmAddress(contractId);
        return DomainUtils.leftPadBytes(
                evmAddress != null ? evmAddress : DomainUtils.toEvmAddress(contractId), ADDRESS_LENGTH);
    }

    /**
     * Resolves a synthetic log's sender/receiver topic to its EVM address alias when available, mirroring the
     * resolution {@code SyntheticLogListener} performs on the persisted log. Non-synthetic logs and null topics are
     * returned unchanged.
     */
    private byte @Nullable [] resolveTopic(final ContractLog contractLog, final byte @Nullable [] topic) {
        if (!contractLog.isSynthetic() || topic == null) {
            return topic;
        }

        final var evmAddress = resolveEvmAddress(DomainUtils.fromTrimmedEvmAddress(topic));
        return evmAddress != null ? evmAddress : topic;
    }

    /**
     * @return the entity's trimmed EVM address alias, or {@code null} if it has none
     */
    private byte @Nullable [] resolveEvmAddress(final EntityId entityId) {
        return EntityId.isEmpty(entityId) ? null : evmAddressById.get(entityId.getId());
    }

    private void writeTopic(final BytesValueRLPOutput out, final byte @Nullable [] topic) {
        if (topic != null) {
            out.writeBytes(Bytes.wrap(DomainUtils.leftPadBytes(topic, WORD_LENGTH)));
        }
    }

    private byte[] data(final byte[] data) {
        if (ArrayUtils.isEmpty(data)) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        return DomainUtils.leftPadBytes(data, WORD_LENGTH);
    }
}
