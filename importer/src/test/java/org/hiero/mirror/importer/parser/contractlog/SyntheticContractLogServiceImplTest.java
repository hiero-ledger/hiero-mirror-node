// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntheticContractLogServiceImplTest {

    private static final int TOPIC_SIZE_BYTES = 32;
    private static final ContractID HOOK_CONTRACT_ADDRESS =
            ContractID.newBuilder().setContractNum(0x16d).build();
    private static final ContractID HTS_PRECOMPILE_CONTRACT_ADDRESS =
            ContractID.newBuilder().setContractNum(0x167).build();

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
        var matchingLog = createMatchingFungibleTokenTransferLog(entityTokenId, senderId, receiverId, amount);

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
    @DisplayName("Should create synthetic contract log with a parent that has no contract result")
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
        recordItem = recordItemBuilder.contractCall().build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        var capturedLog = contractLogCaptor.getValue();

        assertThat(capturedLog.getBloom()).isNotNull();
        assertThat(capturedLog.getBloom()).hasSize(LogsBloomFilter.BYTE_SIZE);

        var expectedBloom = calculateExpectedBloom(entityTokenId, senderId, receiverId, amount);
        assertThat(capturedLog.getBloom()).isEqualTo(expectedBloom);
    }

    @Test
    @DisplayName("Should use empty bloom for non-contract transactions")
    void emptyBloomForNonContractTransactions() {
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        var capturedLog = contractLogCaptor.getValue();

        assertThat(capturedLog.getBloom()).isEqualTo(new byte[] {0});
    }

    @Test
    @DisplayName("Should use parent's consensus timestamp when parent has contract result")
    void useParentConsensusTimestampWhenParentHasContractResult() {
        // Create a parent record item with contract result
        var parentRecordItem = recordItemBuilder.contractCall().build();
        long parentTimestamp = parentRecordItem.getConsensusTimestamp();

        // Create a child record item linked to the parent
        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentTimestamp / 1_000_000_000)
                        .setNanos((int) (parentTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        // Verify child has a different timestamp than parent
        assertThat(recordItem.getConsensusTimestamp()).isNotEqualTo(parentTimestamp);

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        var capturedLog = contractLogCaptor.getValue();

        // The log should use the parent's consensus timestamp since parent has contract result
        assertThat(capturedLog.getConsensusTimestamp()).isEqualTo(parentTimestamp);
    }

    @Test
    @DisplayName("Should use child's consensus timestamp when no parent with contract result")
    void useChildConsensusTimestampWhenNoParentWithContractResult() {
        // Use the default recordItem from @BeforeEach which is a tokenMint (no parent with contract result)
        var childTimestamp = recordItem.getConsensusTimestamp();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        var capturedLog = contractLogCaptor.getValue();

        // The log should use the child's own consensus timestamp since there's no parent with contract result
        assertThat(capturedLog.getConsensusTimestamp()).isEqualTo(childTimestamp);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName(
            "Correct behaviour for hook-related transaction with a ContractCall and a Transfer precompile as childs")
    void correctBehaviourForHookRelatedContractCallWithTransferPrecompile(boolean hasMatchingLog) {
        var matchingLog = createMatchingFungibleTokenTransferLog(entityTokenId, senderId, receiverId, amount);

        // Create a top-level record item for the transaction which is triggering the hook
        var topLevelRecordItem = recordItemBuilder.cryptoTransfer().build();
        var topLevelTimestamp = topLevelRecordItem.getConsensusTimestamp();

        // Create a parent record item with contract result and non-zero nonce (hook scenario).
        // Parent = hook execution (contract 365); child = HTS precompile (contract 0x167).
        var parentContractCallTimestamp = topLevelTimestamp + 1;
        var parentContractCallRecordItem = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(1))
                        .setContractCallResult(ContractFunctionResult.newBuilder()
                                .setContractID(HOOK_CONTRACT_ADDRESS)
                                .addLogInfo(hasMatchingLog ? matchingLog : ContractLoginfo.getDefaultInstance()))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentContractCallTimestamp / 1_000_000_000)
                                .setNanos((int) (parentContractCallTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) topLevelTimestamp % 1_000_000_000)))
                .recordItem(r -> r.previous(topLevelRecordItem))
                .build();

        var childTimestamp = parentContractCallTimestamp + 1;
        // Child record: HTS precompile (0x167), distinct transaction ID (nonce 2) and its own record instance
        recordItem = recordItemBuilder
                .contractCall(HTS_PRECOMPILE_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(2))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HTS_PRECOMPILE_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(childTimestamp / 1_000_000_000)
                                .setNanos((int) (childTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) topLevelTimestamp % 1_000_000_000)))
                .recordItem(r -> r.previous(parentContractCallRecordItem))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(hasMatchingLog ? 0 : 1)).onContractLog(any());
    }

    @Test
    @DisplayName(
            "Correct behaviour for hook-related transaction with a ContractCall and three nested Transfer precompile children")
    void correctBehaviourForHookRelatedContractCallWithThreePrecompileChildren() {
        var matchingLog = createMatchingFungibleTokenTransferLog(entityTokenId, senderId, receiverId, amount);

        // Create a top-level record item for the transaction which is triggering the hook
        var topLevelRecordItem = recordItemBuilder.cryptoTransfer().build();
        var topLevelTimestamp = topLevelRecordItem.getConsensusTimestamp();

        // Create a parent ContractCall record item hook triggered
        var parentContractCallTimestamp = topLevelTimestamp + 1;
        var parentContractCallRecordItem = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(1))
                        .setContractCallResult(ContractFunctionResult.newBuilder()
                                .setContractID(HOOK_CONTRACT_ADDRESS)
                                .addLogInfo(matchingLog)
                                .addLogInfo(matchingLog))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentContractCallTimestamp / 1_000_000_000)
                                .setNanos((int) (parentContractCallTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) (topLevelTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(topLevelRecordItem))
                .build();

        // First nested precompile child (nonce 2)
        var child1Timestamp = parentContractCallTimestamp + 1;
        var child1RecordItem = recordItemBuilder
                .contractCall(HTS_PRECOMPILE_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(2))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HTS_PRECOMPILE_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(child1Timestamp / 1_000_000_000)
                                .setNanos((int) (child1Timestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) (topLevelTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(parentContractCallRecordItem))
                .build();

        // Second nested precompile child (nonce 3)
        var child2Timestamp = child1Timestamp + 1;
        var child2RecordItem = recordItemBuilder
                .contractCall(HTS_PRECOMPILE_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(3))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HTS_PRECOMPILE_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(child2Timestamp / 1_000_000_000)
                                .setNanos((int) (child2Timestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) (topLevelTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(child1RecordItem))
                .build();

        // Third nested precompile child (nonce 4)
        var child3Timestamp = child2Timestamp + 1;
        var child3RecordItem = recordItemBuilder
                .contractCall(HTS_PRECOMPILE_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(4))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HTS_PRECOMPILE_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(child3Timestamp / 1_000_000_000)
                                .setNanos((int) (child3Timestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) (topLevelTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(child2RecordItem))
                .build();

        // We have 2 occurrences of the logs already in the ContractCall parent, so only the last child
        // will create a synthetic record
        syntheticContractLogService.create(
                new TransferContractLog(child1RecordItem, entityTokenId, senderId, receiverId, amount));
        syntheticContractLogService.create(
                new TransferContractLog(child2RecordItem, entityTokenId, senderId, receiverId, amount));
        syntheticContractLogService.create(
                new TransferContractLog(child3RecordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener, times(1)).onContractLog(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Correct behaviour for a hook-related transaction with multiple ContractCalls as childs")
    void correctBehaviourForHookWithMultipleCallChildRecords(boolean hasMatchingLog) {
        var matchingLog = createMatchingFungibleTokenTransferLog(entityTokenId, senderId, receiverId, amount);

        // Create a top-level record item for the transaction which is triggering the hook
        var topLevelRecordItem = recordItemBuilder.cryptoTransfer().build();
        var topLevelTimestamp = topLevelRecordItem.getConsensusTimestamp();

        // 1st child record: ContractCall to HOOK contract
        var firstChildContractCallTimestamp = topLevelTimestamp + 1;
        var firstChildContractCallRecordItem = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(1))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HOOK_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(firstChildContractCallTimestamp / 1_000_000_000)
                                .setNanos((int) (firstChildContractCallTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) (topLevelTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(topLevelRecordItem))
                .build();

        var secondChildContractCallTimestamp = firstChildContractCallTimestamp + 1;

        // 2nd child record: ContractCall to HOOK contract
        var secondChildContractCallRecordItem = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(2))
                        .setContractCallResult(ContractFunctionResult.newBuilder()
                                .setContractID(HOOK_CONTRACT_ADDRESS)
                                .addLogInfo(hasMatchingLog ? matchingLog : ContractLoginfo.getDefaultInstance()))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(secondChildContractCallTimestamp / 1_000_000_000)
                                .setNanos((int) (secondChildContractCallTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) (topLevelTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(firstChildContractCallRecordItem))
                .build();

        syntheticContractLogService.create(new TransferContractLog(
                secondChildContractCallRecordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(hasMatchingLog ? 0 : 1)).onContractLog(any());
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

        syntheticContractLogService.create(
                new TransferIndexedContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create one of two identical synthetic logs when parent has single matching occurrence")
    void createOneOfTwoIdenticalLogsWhenSingleOccurrenceInParent() {
        // Create a parent with a single matching contract log
        var matchingLog = createMatchingFungibleTokenTransferLog(entityTokenId, senderId, receiverId, amount);

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

        var transferLog = new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount);

        // First call: matches the single occurrence in parent, so it is skipped
        syntheticContractLogService.create(transferLog);
        // Second call: occurrence consumed, no more matches, so it is created
        syntheticContractLogService.create(transferLog);

        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should skip both identical synthetic logs when parent has two matching occurrences")
    void skipBothIdenticalLogsWhenTwoOccurrencesInParent() {
        // Create a parent with two identical matching contract logs
        var matchingLog = createMatchingFungibleTokenTransferLog(entityTokenId, senderId, receiverId, amount);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setContractCallResult(ContractFunctionResult.newBuilder()
                        .addLogInfo(matchingLog)
                        .addLogInfo(matchingLog)))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        var transferLog = new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount);

        // Both calls match an occurrence in the parent, so both are skipped
        syntheticContractLogService.create(transferLog);
        syntheticContractLogService.create(transferLog);

        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should skip creation of a log that is already present within the same RecordItem being imported")
    void skipAlreadyPresentContractLog() {
        var matchingLog = createMatchingFungibleTokenTransferLog(entityTokenId, senderId, receiverId, amount);

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setContractCallResult(
                        ContractFunctionResult.newBuilder().addLogInfo(matchingLog)))
                .build();

        var transferLog = new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount);

        syntheticContractLogService.create(transferLog);

        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create one of three identical synthetic logs when parent has two matching occurrences")
    void createOneOfThreeIdenticalLogsWhenTwoOccurrencesInParent() {
        // Create a parent with two identical matching contract logs
        var matchingLog = createMatchingFungibleTokenTransferLog(entityTokenId, senderId, receiverId, amount);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setContractCallResult(ContractFunctionResult.newBuilder()
                        .addLogInfo(matchingLog)
                        .addLogInfo(matchingLog)))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        var transferLog = new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount);

        // First two calls match the two occurrences in parent, so they are skipped
        syntheticContractLogService.create(transferLog);
        syntheticContractLogService.create(transferLog);
        // Third call: both occurrences consumed, so this one is created
        syntheticContractLogService.create(transferLog);

        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should skip both different synthetic logs when parent has one of each matching occurrence")
    void skipBothDifferentLogsWhenEachHasMatchingOccurrenceInParent() {
        var secondReceiverId = EntityId.of(0, 0, 999);
        var secondAmount = 500L;

        // Create a parent with two different matching contract logs
        var matchingLog1 = createMatchingFungibleTokenTransferLog(entityTokenId, senderId, receiverId, amount);
        var matchingLog2 =
                createMatchingFungibleTokenTransferLog(entityTokenId, senderId, secondReceiverId, secondAmount);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setContractCallResult(ContractFunctionResult.newBuilder()
                        .addLogInfo(matchingLog1)
                        .addLogInfo(matchingLog2)))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        // Each synthetic log matches a different occurrence in the parent, so both are skipped
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, secondReceiverId, secondAmount));

        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should skip duplicate synthetic log but create different one when parent has only the first log")
    void createSkipSyntheticLogButCreateDifferentWhenParentHasOnlyFirstLog() {
        var secondReceiverId = EntityId.of(0, 0, 999);
        var secondAmount = 500L;

        // Parent only has the second (different) log, not the first
        var matchingLog2 =
                createMatchingFungibleTokenTransferLog(entityTokenId, senderId, secondReceiverId, secondAmount);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setContractCallResult(
                        ContractFunctionResult.newBuilder().addLogInfo(matchingLog2)))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        // First log has no match in parent → created
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        // Second log matches the parent → skipped
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, secondReceiverId, secondAmount));

        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName(
            "Should not create synthetic log when multi-party transfer is disabled and child has more than 2 token transfer lists")
    void doNotCreateWhenMultiPartyDisabledAndChildHasMoreThanTwoTokenTransferLists() {
        entityProperties.getPersist().setSyntheticContractLogsMulti(false);

        // Create a parent with contract result (required for the multi-party check)
        var parentRecordItem = recordItemBuilder.contractCall().build();

        // Create a child record item with more than 2 fungible token transfer lists (multi-party scenario)
        var tokenId2 = EntityId.of(0, 0, 1000).toTokenID();
        var tokenId3 = EntityId.of(0, 0, 1001).toTokenID();
        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                                .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000)))
                        .addTokenTransferLists(fungibleTokenTransferList(
                                entityTokenId.toTokenID(),
                                tokenTransfer(0, 0, 100, -100L),
                                tokenTransfer(0, 0, 200, 50L),
                                tokenTransfer(0, 0, 300, 50L)))
                        .addTokenTransferLists(fungibleTokenTransferList(
                                tokenId2, tokenTransfer(0, 0, 100, -50L), tokenTransfer(0, 0, 200, 50L)))
                        .addTokenTransferLists(fungibleTokenTransferList(tokenId3, tokenTransfer(0, 0, 300, 100L))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        // Multi-party is disabled and child has >2 token transfer lists → skip
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName(
            "Should create synthetic log when multi-party transfer is enabled and child has more than 2 token transfer lists")
    void createWhenMultiPartyEnabledAndChildHasMoreThanTwoTokenTransferLists() {
        var parentRecordItem = recordItemBuilder.contractCall().build();

        // Create a child record item with more than 2 fungible token transfer lists (multi-party scenario)
        var tokenId2 = EntityId.of(0, 0, 1000).toTokenID();
        var tokenId3 = EntityId.of(0, 0, 1001).toTokenID();
        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                                .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000)))
                        .addTokenTransferLists(fungibleTokenTransferList(
                                entityTokenId.toTokenID(),
                                tokenTransfer(0, 0, 100, -100L),
                                tokenTransfer(0, 0, 200, 50L),
                                tokenTransfer(0, 0, 300, 50L)))
                        .addTokenTransferLists(fungibleTokenTransferList(
                                tokenId2, tokenTransfer(0, 0, 100, -50L), tokenTransfer(0, 0, 200, 50L)))
                        .addTokenTransferLists(fungibleTokenTransferList(tokenId3, tokenTransfer(0, 0, 300, 100L))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        // Multi-party is enabled (default), so log should be created even with >2 token transfer lists
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName(
            "Should create synthetic log when multi-party transfer is disabled but child has exactly 2 token transfer lists")
    void createWhenMultiPartyDisabledAndChildHasExactlyTwoTokenTransferLists() {
        entityProperties.getPersist().setSyntheticContractLogsMulti(false);

        // Create a parent with contract result
        var parentRecordItem = recordItemBuilder.contractCall().build();

        // Create a child record item with exactly 2 fungible token transfer lists (not multi-party for skip)
        var tokenId2 = EntityId.of(0, 0, 1000).toTokenID();
        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                                .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000)))
                        .addTokenTransferLists(fungibleTokenTransferList(
                                entityTokenId.toTokenID(),
                                tokenTransfer(0, 0, 100, -100L),
                                tokenTransfer(0, 0, 200, 100L)))
                        .addTokenTransferLists(fungibleTokenTransferList(
                                tokenId2, tokenTransfer(0, 0, 200, -50L), tokenTransfer(0, 0, 300, 50L))))
                .recordItem(r -> r.previous(parentRecordItem))
                .build();

        // Only 2 token transfer lists - log should be created
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(any());
    }

    @Test
    @DisplayName("Should not create synthetic log when multi-party disabled and no contract-related parent")
    void skipCreationWhenMultiPartyDisabledAndNoContractParent() {
        entityProperties.getPersist().setSyntheticContractLogsMulti(false);

        var tokenId2 = EntityId.of(0, 0, 1000).toTokenID();
        var tokenId3 = EntityId.of(0, 0, 1001).toTokenID();
        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.addTokenTransferLists(fungibleTokenTransferList(
                                entityTokenId.toTokenID(),
                                tokenTransfer(0, 0, 100, -100L),
                                tokenTransfer(0, 0, 200, 50L),
                                tokenTransfer(0, 0, 300, 50L)))
                        .addTokenTransferLists(fungibleTokenTransferList(
                                tokenId2, tokenTransfer(0, 0, 100, -50L), tokenTransfer(0, 0, 200, 50L)))
                        .addTokenTransferLists(fungibleTokenTransferList(tokenId3, tokenTransfer(0, 0, 300, 100L))))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    private AccountAmount tokenTransfer(long shard, long realm, long num, long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder()
                        .setShardNum(shard)
                        .setRealmNum(realm)
                        .setAccountNum(num))
                .setAmount(amount)
                .build();
    }

    private TokenTransferList fungibleTokenTransferList(TokenID tokenId, AccountAmount... transfers) {
        var builder = TokenTransferList.newBuilder().setToken(tokenId);
        for (AccountAmount t : transfers) {
            builder.addTransfers(t);
        }
        return builder.build();
    }

    /**
     * Creates a ContractLoginfo that matches the topics and data of a fungible token TransferContractLog.
     * This simulates an ERC-20 Transfer event emitted by a token contract.
     * Topics and data use the raw trimmed bytes (without left-padding) to match
     * the format that Utility.getTopic/getDataTrimmed will produce after trimming.
     *
     * @param tokenId the token that emitted the log (contract)
     * @param sender the sender address (topic1)
     * @param receiver the receiver address (topic2)
     * @param transferAmount the transfer amount (data)
     * @return a ContractLoginfo matching a fungible token transfer
     */
    private ContractLoginfo createMatchingFungibleTokenTransferLog(
            EntityId tokenId, EntityId sender, EntityId receiver, long transferAmount) {

        var topic0 = ByteString.copyFrom(AbstractSyntheticContractLog.TRANSFER_SIGNATURE);
        var topic1 = ByteString.copyFrom(entityIdToBytes(sender));
        var topic2 = ByteString.copyFrom(entityIdToBytes(receiver));

        var data = ByteString.copyFrom(
                DomainUtils.trim(Bytes.ofUnsignedLong(transferAmount).toArrayUnsafe()));

        return ContractLoginfo.newBuilder()
                .setContractID(tokenId.toContractID())
                .addTopic(topic0)
                .addTopic(topic1)
                .addTopic(topic2)
                .setData(data)
                .build();
    }

    private byte[] entityIdToBytes(EntityId entityId) {
        if (EntityId.isEmpty(entityId)) {
            return new byte[0];
        }
        return DomainUtils.trim(DomainUtils.toEvmAddress(entityId));
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

        // Topic 1: Sender (padded) — mirrors addTopicIfPresent which checks for null, not empty
        var senderBytes = entityIdToBytes(sender);
        topics.add(LogTopic.wrap(Bytes.wrap(DomainUtils.leftPadBytes(senderBytes, TOPIC_SIZE_BYTES))));

        // Topic 2: Receiver (padded)
        var receiverBytes = entityIdToBytes(receiver);
        topics.add(LogTopic.wrap(Bytes.wrap(DomainUtils.leftPadBytes(receiverBytes, TOPIC_SIZE_BYTES))));

        // Data: Amount
        var amountBytes = DomainUtils.trim(Bytes.ofUnsignedLong(transferAmount).toArrayUnsafe());
        var data = amountBytes != null ? Bytes.wrap(amountBytes) : Bytes.EMPTY;

        var besuLog = new Log(logger, data, topics);
        return LogsBloomFilter.builder().insertLog(besuLog).build().toArray();
    }
}
