// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.jspecify.annotations.Nullable;

/**
 * Converts a single transaction's contract result and logs into the {@link Receipt} used to compute the
 * receipts-trie root.
 */
final class ReceiptAssembler {

    private static final int ADDRESS_LENGTH = 20;
    private static final int WORD_LENGTH = 32;
    private static final Set<Integer> SUCCESS_TRANSACTION_RESULTS = Set.of(
            ResponseCodeEnum.SUCCESS_VALUE,
            ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED_VALUE,
            ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION_VALUE);

    private ReceiptAssembler() {}

    /**
     * Builds the receipt for a single transaction.
     *
     * @param contractResult the transaction's contract result; null for a synthetic receipt built from logs only
     * @param contractLogs   the transaction's logs in log index order
     * @param ethereumType   the EIP-2718 ethereum transaction type; null for non-ethereum transactions
     * @param evmAddressById the EVM address aliases of the contracts emitting the logs
     */
    static Receipt toReceipt(
            @Nullable final ContractResult contractResult,
            final Collection<ContractLog> contractLogs,
            @Nullable final Integer ethereumType,
            final Map<Long, byte[]> evmAddressById) {
        final var receiptLogs = new ArrayList<Receipt.ReceiptLog>(contractLogs.size());
        for (final var contractLog : contractLogs) {
            receiptLogs.add(new Receipt.ReceiptLog(
                    logAddress(contractLog, evmAddressById), topics(contractLog), data(contractLog.getData())));
        }

        if (contractResult != null) {
            return new Receipt(
                    contractResult.getTransactionIndex() != null ? contractResult.getTransactionIndex() : 0,
                    ethereumType != null ? ethereumType : 0,
                    SUCCESS_TRANSACTION_RESULTS.contains(contractResult.getTransactionResult()),
                    false,
                    contractResult.getGasUsed() != null ? contractResult.getGasUsed() : 0L,
                    contractResult.getBloom(),
                    receiptLogs);
        }

        // Logs without a contract result (e.g. HTS transactions whose synthetic contract result isn't persisted)
        // become a synthetic receipt, encoded with 32 zero bytes in place of the status like the relay does
        final var firstLog = contractLogs.iterator().next();
        return new Receipt(
                firstLog.getTransactionIndex() != null ? firstLog.getTransactionIndex() : 0,
                0,
                true,
                true,
                0L,
                syntheticBloom(receiptLogs),
                receiptLogs);
    }

    private static byte[] logAddress(final ContractLog contractLog, final Map<Long, byte[]> evmAddressById) {
        final var contractId = contractLog.getContractId();
        if (EntityId.isEmpty(contractId)) {
            return new byte[ADDRESS_LENGTH];
        }

        final var evmAddress = evmAddressById.get(contractId.getId());
        return DomainUtils.leftPadBytes(
                evmAddress != null ? evmAddress : DomainUtils.toEvmAddress(contractId), ADDRESS_LENGTH);
    }

    private static List<byte[]> topics(final ContractLog contractLog) {
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

    private static byte[] data(final byte[] data) {
        if (ArrayUtils.isEmpty(data)) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        return data.length < WORD_LENGTH ? DomainUtils.leftPadBytes(data, WORD_LENGTH) : data;
    }

    private static byte[] syntheticBloom(final List<Receipt.ReceiptLog> logs) {
        var bloom = new byte[LogsBloomFilter.BYTE_SIZE];
        for (final var log : logs) {
            final var filter = new LogsBloomFilter();
            filter.insertAddress(log.address());
            for (final var topic : log.topics()) {
                filter.insertTopic(topic);
            }
            bloom = LogsBloomFilter.or(filter.toArrayUnsafe(), bloom);
        }

        return bloom;
    }
}
