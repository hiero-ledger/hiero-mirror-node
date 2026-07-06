// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.receipt;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;

/**
 * Builds the ordered list of receipts for a single block from its contract results.
 */
@Named
public class ReceiptAssembler {

    private static final int ADDRESS_LENGTH = 20;
    private static final int WORD_LENGTH = 32;
    private static final byte[] SYNTHETIC_ROOT = new byte[32];
    private static final Set<Integer> SUCCESS_TRANSACTION_RESULTS = Set.of(
            ResponseCodeEnum.SUCCESS_VALUE,
            ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED_VALUE,
            ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION_VALUE);

    /**
     * Assembles the block's receipts (unsorted; the calculator sorts by transaction index)
     */
    public List<Receipt> assemble(
            final Collection<ContractResult> contractResults,
            final Collection<ContractLog> contractLogs,
            final Map<Long, Integer> typeByConsensusTimestamp,
            final Map<Long, byte[]> evmAddressById) {
        final var timestamps = new LinkedHashSet<Long>();

        final var resultsByTimestamp = new HashMap<Long, ContractResult>();
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

        if (timestamps.isEmpty()) {
            return List.of();
        }

        final var receipts = new ArrayList<Receipt>(timestamps.size());
        for (final var timestamp : timestamps) {
            final var logs =
                    logsByTimestamp.getOrDefault(timestamp, new TreeMap<>()).values();
            final var receiptLogs = new ArrayList<Receipt.ReceiptLog>(logs.size());
            for (final var contractLog : logs) {
                receiptLogs.add(new Receipt.ReceiptLog(
                        logAddress(contractLog, evmAddressById), topics(contractLog), data(contractLog.getData())));
            }

            final var contractResult = resultsByTimestamp.get(timestamp);
            if (contractResult != null) {
                receipts.add(new Receipt(
                        contractResult.getTransactionIndex() != null ? contractResult.getTransactionIndex() : 0,
                        typeByConsensusTimestamp.getOrDefault(timestamp, 0),
                        SUCCESS_TRANSACTION_RESULTS.contains(contractResult.getTransactionResult()),
                        null,
                        contractResult.getGasUsed() != null ? contractResult.getGasUsed() : 0L,
                        contractResult.getBloom(),
                        receiptLogs));
            } else {
                final var firstLog = logsByTimestamp.get(timestamp).firstEntry().getValue();
                receipts.add(new Receipt(
                        firstLog.getTransactionIndex(),
                        0,
                        true,
                        SYNTHETIC_ROOT,
                        0L,
                        syntheticBloom(receiptLogs),
                        receiptLogs));
            }
        }

        return receipts;
    }

    private byte[] logAddress(final ContractLog contractLog, final Map<Long, byte[]> evmAddressById) {
        final var contractId = contractLog.getContractId();
        if (EntityId.isEmpty(contractId)) {
            return new byte[ADDRESS_LENGTH];
        }

        final var evmAddress = evmAddressById.get(contractId.getId());
        return leftPad(evmAddress != null ? evmAddress : DomainUtils.toEvmAddress(contractId), ADDRESS_LENGTH);
    }

    private List<byte[]> topics(final ContractLog contractLog) {
        final var topics = new ArrayList<byte[]>(4);
        for (final var topic : new byte[][] {
            contractLog.getTopic0(), contractLog.getTopic1(), contractLog.getTopic2(), contractLog.getTopic3()
        }) {
            if (topic != null) {
                topics.add(leftPad(topic, WORD_LENGTH));
            }
        }

        return topics;
    }

    private byte[] data(final byte[] data) {
        if (ArrayUtils.isEmpty(data)) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        return data.length < WORD_LENGTH ? leftPad(data, WORD_LENGTH) : data;
    }

    private byte[] syntheticBloom(final List<Receipt.ReceiptLog> logs) {
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

    private byte[] leftPad(final byte[] value, final int length) {
        if (value.length == length) {
            return value;
        }

        if (value.length > length) {
            return Arrays.copyOfRange(value, value.length - length, value.length);
        }

        final var padded = new byte[length];
        System.arraycopy(value, 0, padded, length - value.length, value.length);
        return padded;
    }
}
