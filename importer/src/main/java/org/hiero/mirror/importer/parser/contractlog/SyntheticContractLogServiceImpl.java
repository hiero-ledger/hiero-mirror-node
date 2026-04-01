// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import jakarta.inject.Named;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.exception.InvalidEntityException;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;

@Named
@RequiredArgsConstructor
public class SyntheticContractLogServiceImpl implements SyntheticContractLogService {

    /** Upper bound for {@code accountNum} in {@link EntityId#of(long, long, long)} (38-bit num). */
    private static final long MAX_ENTITY_NUM_LONG = (1L << 38) - 1L;

    private static final BigInteger MAX_ENTITY_NUM = BigInteger.valueOf(MAX_ENTITY_NUM_LONG);

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final EntityIdService entityIdService;
    private final byte[] empty = Bytes.of(0).toArray();

    @Override
    public void create(SyntheticContractLog log) {
        if (!entityProperties.getPersist().isSyntheticContractLogs()) {
            return;
        }

        var recordItem = log.getRecordItem();
        var contractRelatedParentRecordItem = recordItem.getContractRelatedParent();

        // We will backfill any EVM-related fungible token transfers that don't have synthetic events produced by CN
        if (isContract(recordItem) && shouldSkipLogCreationForContractTransfer(log)) {
            return;
        }

        long consensusTimestamp = contractRelatedParentRecordItem != null
                ? contractRelatedParentRecordItem.getConsensusTimestamp()
                : recordItem.getConsensusTimestamp();
        int logIndex = recordItem.getAndIncrementLogIndex();

        ContractLog contractLog = new ContractLog();

        contractLog.setBloom(isContract(recordItem) ? createBloom(log) : empty);
        contractLog.setConsensusTimestamp(consensusTimestamp);
        contractLog.setContractId(log.getEntityId());
        contractLog.setData(log.getData() != null ? log.getData() : empty);
        contractLog.setIndex(logIndex);
        contractLog.setRootContractId(log.getEntityId());
        contractLog.setPayerAccountId(recordItem.getPayerAccountId());
        contractLog.setTopic0(log.getTopic0());
        contractLog.setTopic1(log.getTopic1());
        contractLog.setTopic2(log.getTopic2());
        contractLog.setTopic3(log.getTopic3());
        contractLog.setTransactionIndex(recordItem.getTransactionIndex());

        byte[] transactionHash = contractRelatedParentRecordItem != null
                ? contractRelatedParentRecordItem.getTransactionHash()
                : recordItem.getTransactionHash();
        contractLog.setTransactionHash(transactionHash);
        contractLog.setSyntheticTransfer(log instanceof TransferContractLog);

        entityListener.onContractLog(contractLog);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult()
                || recordItem.getTransactionRecord().hasContractCreateResult();
    }

    private boolean shouldSkipLogCreationForContractTransfer(SyntheticContractLog syntheticLog) {
        if (!(syntheticLog instanceof TransferContractLog transferLog)) {
            // Only TransferContractLog synthetic event creation is supported for an operation with contract origin
            return true;
        }

        int tokenTransfersCount =
                syntheticLog.getRecordItem().getTransactionRecord().getTokenTransferListsCount();
        if (tokenTransfersCount > 2 && !entityProperties.getPersist().isSyntheticContractLogsMulti()) {
            // We have a multi-party fungible transfer scenario and synthetic event creation for
            // such transfers is disabled
            return true;
        }

        return logAlreadyImported(transferLog);
    }

    /**
     * Checks if the given TransferContractLog matches an existing contract log in the record itself or in the parent
     * record item and consumes one occurrence of the matching log. This handles the case where the same contract log
     * appears multiple times in the child records as being part of different operations.
     *
     * @param transferLog the TransferContractLog to check
     * @return true if a matching log is found and it is already persisted, false otherwise
     */
    private boolean logAlreadyImported(TransferContractLog transferLog) {
        return transferLog
                .getRecordItem()
                .consumeMatchingContractLog(
                        transferLog.getTopic0(),
                        transferLog.getTopic1(),
                        transferLog.getTopic2(),
                        transferLog.getTopic3(),
                        transferLog.getData(),
                        this::resolveContractLogTopicAccount);
    }

    private Optional<EntityId> resolveContractLogTopicAccount(byte[] accountAddress) {
        if (ArrayUtils.isEmpty(accountAddress)) {
            return Optional.of(EntityId.EMPTY);
        }

        if (accountAddress.length <= 16) {
            if (accountAddress.length == 0 || accountAddress.length > Long.BYTES) {
                return Optional.empty();
            }

            final var paddedAddress = new byte[Long.BYTES];

            // zero-pad on the left
            System.arraycopy(
                    accountAddress, 0, paddedAddress, Long.BYTES - accountAddress.length, accountAddress.length);

            final var accountNum =
                    ByteBuffer.wrap(paddedAddress).order(ByteOrder.BIG_ENDIAN).getLong();

            final var common = CommonProperties.getInstance();
            try {
                return Optional.of(EntityId.of(common.getShard(), common.getRealm(), accountNum));
            } catch (InvalidEntityException e) {
                return Optional.empty();
            }
        }

        return entityIdService.lookup(AccountID.newBuilder()
                .setAlias(ByteString.copyFrom(accountAddress))
                .build());
    }

    /**
     * Creates a bloom filter for a synthetic contract log using the log's address, topics, and data.
     *
     * @param log the synthetic contract log
     * @return the bloom filter as a byte array
     */
    private byte[] createBloom(SyntheticContractLog log) {
        final var evmAddress = DomainUtils.toEvmAddress(log.getEntityId());
        final var logsBloomFilter = new LogsBloomFilter();
        logsBloomFilter.insertAddress(evmAddress);
        logsBloomFilter.insertTopic(log.getTopic0());
        logsBloomFilter.insertTopic(log.getTopic1());
        logsBloomFilter.insertTopic(log.getTopic2());
        logsBloomFilter.insertTopic(log.getTopic3());
        return logsBloomFilter.toArrayUnsafe();
    }
}
