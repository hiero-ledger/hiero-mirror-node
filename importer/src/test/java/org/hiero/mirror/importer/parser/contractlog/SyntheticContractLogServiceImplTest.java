// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord.Builder;
import java.util.ArrayList;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticContractLogServiceImplTest {

    private static final int TOPIC_SIZE_BYTES = 32;

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final EntityProperties entityProperties = new EntityProperties(new SystemEntity(new CommonProperties()));

    @Mock
    private EntityListener entityListener;

    @Captor
    private ArgumentCaptor<ContractLog> contractLogCaptor;

    private SyntheticContractLogService syntheticContractLogService;

    private RecordItem recordItem;
    private EntityId entityTokenId;
    private EntityId senderId;
    private EntityId receiverId;
    private long amount;

    @BeforeEach
    void beforeEach() {
        syntheticContractLogService = new SyntheticContractLogServiceImpl(entityListener, entityProperties);
        recordItem = recordItemBuilder.tokenMint(TokenType.FUNGIBLE_COMMON).build();

        TokenID tokenId = recordItem.getTransactionBody().getTokenMint().getToken();
        entityTokenId = EntityId.of(tokenId);
        senderId = EntityId.EMPTY;
        receiverId =
                EntityId.of(recordItem.getTransactionBody().getTransactionID().getAccountID());
        amount = recordItem.getTransactionBody().getTokenMint().getAmount();
    }

    @Test
    @DisplayName("Should be able to create valid synthetic contract log")
    void createValid() {
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should be able to create valid synthetic contract log with indexed value")
    void createValidIndexed() {
        syntheticContractLogService.create(
                new TransferIndexedContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create synthetic contract log when parent has logs that don't match")
    void createWhenParentLogsDoNotMatch() {
        // Create a parent with contract logs that don't match the synthetic log
        var parentRecordItem = recordItemBuilder.contractCall().build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        // The parent has random logs that don't match the synthetic transfer log
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should not create synthetic contract log when it matches existing parent log")
    void doNotCreateWhenMatchingParentLog() {
        // Create a ContractLoginfo that matches the TransferContractLog
        var matchingLog = createMatchingContractLoginfo(entityTokenId, senderId, receiverId, amount);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setContractCallResult(
                        ContractFunctionResult.newBuilder().addLogInfo(matchingLog)))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create synthetic contract log when contract parent is null")
    void createWhenContractParentIsNull() {
        // Create a contract call without a previous item that has contract results
        recordItem = recordItemBuilder.contractCall().build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create synthetic contract log with contract with existing parent logs")
    void createWithContractWithNoParentLogs() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(Builder::clearContractCallResult)
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should not create synthetic contract log with entity property turned off")
    void createTurnedOff() {
        entityProperties.getPersist().setSyntheticContractLogs(false);
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should populate bloom filter correctly for contract transactions")
    void bloomFilterPopulated() {
        // Use a contract call record item so bloom is calculated
        recordItem = recordItemBuilder.contractCall().build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        var capturedLog = contractLogCaptor.getValue();

        // Verify bloom is not empty or single zero byte
        assertThat(capturedLog.getBloom()).isNotNull();
        assertThat(capturedLog.getBloom().length).isEqualTo(LogsBloomFilter.BYTE_SIZE);

        // Verify bloom matches expected calculation
        var expectedBloom = calculateExpectedBloom(entityTokenId, senderId, receiverId, amount);
        assertThat(capturedLog.getBloom()).isEqualTo(expectedBloom);
    }

    @Test
    @DisplayName("Should use empty bloom for non-contract transactions")
    void emptyBloomForNonContractTransactions() {
        // recordItem from setUp is a tokenMint (non-contract)
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        var capturedLog = contractLogCaptor.getValue();

        // Verify bloom is the empty byte array
        assertThat(capturedLog.getBloom()).isNotNull();
        assertThat(capturedLog.getBloom()).isEqualTo(new byte[] {0});
    }

    @Test
    @DisplayName("Should handle hook-related transaction with 1 nanosecond difference")
    void hookRelatedTransactionWithOneNanosecondDifference() {
        // Create a parent record item with contract result
        var parentRecordItem = recordItemBuilder.contractCall().build();
        long parentTimestamp = parentRecordItem.getConsensusTimestamp();

        // Create a child with exactly 1 nanosecond difference (hook scenario)
        long childTimestamp = parentTimestamp + 1;

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(childTimestamp / 1_000_000_000)
                        .setNanos((int) (childTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        // The parent has random logs that don't match
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should not create when hook transaction matches parent log with 1ns difference")
    void doNotCreateWhenHookMatchesParentLog() {
        // Create a ContractLoginfo that matches the TransferContractLog
        var matchingLog = createMatchingContractLoginfo(entityTokenId, senderId, receiverId, amount);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setContractCallResult(
                        ContractFunctionResult.newBuilder().addLogInfo(matchingLog)))
                .build();
        long parentTimestamp = parentRecordItem.getConsensusTimestamp();

        // Create a child with exactly 1 nanosecond difference (hook scenario)
        long childTimestamp = parentTimestamp + 1;

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(childTimestamp / 1_000_000_000)
                        .setNanos((int) (childTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should not create for non-TransferContractLog types in contract transactions")
    void doNotCreateForNonTransferContractLogInContractTransaction() {
        // Create a parent with contract logs
        var parentRecordItem = recordItemBuilder.contractCall().build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        // Non-TransferContractLog types (like TransferIndexedContractLog) should NOT be created
        // for contract transactions - only TransferContractLog is supported
        syntheticContractLogService.create(
                new TransferIndexedContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create TransferIndexedContractLog for non-contract transactions")
    void createTransferIndexedContractLogForNonContractTransaction() {
        // recordItem from setUp is a tokenMint (non-contract transaction)
        syntheticContractLogService.create(
                new TransferIndexedContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    /**
     * Creates a ContractLoginfo that matches the topics and data of a TransferContractLog
     */
    private ContractLoginfo createMatchingContractLoginfo(
            EntityId tokenId, EntityId sender, EntityId receiver, long transferAmount) {

        // Create topics matching TransferContractLog format
        var topic0 = ByteString.copyFrom(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        var topic1 = ByteString.copyFrom(
                DomainUtils.leftPadBytes(DomainUtils.trim(DomainUtils.toEvmAddress(sender)), TOPIC_SIZE_BYTES));
        var topic2 = ByteString.copyFrom(
                DomainUtils.leftPadBytes(DomainUtils.trim(DomainUtils.toEvmAddress(receiver)), TOPIC_SIZE_BYTES));

        // Create data matching the amount
        var data = ByteString.copyFrom(DomainUtils.leftPadBytes(
                DomainUtils.trim(Bytes.ofUnsignedLong(transferAmount).toArrayUnsafe()), TOPIC_SIZE_BYTES));

        return ContractLoginfo.newBuilder()
                .addTopic(topic0)
                .addTopic(topic1)
                .addTopic(topic2)
                .setData(data)
                .build();
    }

    /**
     * Calculates the expected bloom filter for a TransferContractLog
     */
    private byte[] calculateExpectedBloom(EntityId tokenId, EntityId sender, EntityId receiver, long transferAmount) {
        var logger = Address.wrap(Bytes.wrap(DomainUtils.toEvmAddress(tokenId)));
        var topics = new ArrayList<LogTopic>();

        // Topic 0: Transfer signature
        topics.add(LogTopic.wrap(Bytes.wrap(
                DomainUtils.leftPadBytes(AbstractSyntheticContractLog.TRANSFER_SIGNATURE, TOPIC_SIZE_BYTES))));

        // Topic 1: Sender (padded)
        var senderBytes = DomainUtils.trim(DomainUtils.toEvmAddress(sender));
        if (senderBytes.length > 0) {
            topics.add(LogTopic.wrap(Bytes.wrap(DomainUtils.leftPadBytes(senderBytes, TOPIC_SIZE_BYTES))));
        }

        // Topic 2: Receiver (padded)
        var receiverBytes = DomainUtils.trim(DomainUtils.toEvmAddress(receiver));
        if (receiverBytes.length > 0) {
            topics.add(LogTopic.wrap(Bytes.wrap(DomainUtils.leftPadBytes(receiverBytes, TOPIC_SIZE_BYTES))));
        }

        // Data: Amount
        var amountBytes = DomainUtils.trim(Bytes.ofUnsignedLong(transferAmount).toArrayUnsafe());
        var data = amountBytes != null ? Bytes.wrap(amountBytes) : Bytes.EMPTY;

        var besuLog = new Log(logger, data, topics);
        return LogsBloomFilter.builder().insertLog(besuLog).build().toArray();
    }
}
