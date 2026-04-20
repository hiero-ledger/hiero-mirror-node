// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.parser.contractlog.SyntheticContractLogServiceImpl.HAPI_SYNTHETIC_LOG_VERSION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord.Builder;
import java.util.Arrays;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.RecordItemBuilder.TransferType;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Version;

@ExtendWith(MockitoExtension.class)
final class SyntheticContractLogServiceImplTest {

    private static final Version OLD_HAPI_VERSION = new Version(0, 70, 0);
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
        verify(entityListener, times(1)).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(recordItem.getTransactionHash());
    }

    @Test
    @DisplayName("Should be able to create valid synthetic contract log with indexed value")
    void createValidIndexed() {
        var senderEntityId = EntityId.EMPTY;
        var receiverEntityId =
                EntityId.of(recordItem.getTransactionBody().getTransactionID().getAccountID());
        syntheticContractLogService.create(
                new TransferIndexedContractLog(recordItem, entityTokenId, senderEntityId, receiverEntityId, amount));
        verify(entityListener, times(1)).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(recordItem.getTransactionHash());
    }

    @Test
    @DisplayName("Should skip synthetic contract log for HAPI version >= 0.71.0")
    void skipSyntheticLogForNewHapiVersion() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, EntityId.EMPTY, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should skip synthetic contract log for HAPI version >= 0.71.0 with empty receiver")
    void skipSyntheticLogForNewHapiVersionWithEmptyReceiver() {
        var validSender =
                EntityId.of(recordItem.getTransactionBody().getTransactionID().getAccountID());

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, validSender, EntityId.EMPTY, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create synthetic contract log for old HAPI version with parent")
    void createWhenOldHapiVersionWithParent() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(parentRecordItem.getTransactionHash());
    }

    @Test
    @DisplayName("Should skip synthetic contract log for new HAPI version even when no parent")
    void skipSyntheticLogForNewHapiVersionNoParent() {
        recordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should skip synthetic contract log for new HAPI version even with Long.MAX_VALUE IDs")
    void skipSyntheticLogForNewHapiVersionWithLongMaxValue() {
        final var senderMaxValue = EntityId.of(Long.MAX_VALUE);
        final var receiverMaxValue = EntityId.of(Long.MAX_VALUE - 1);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderMaxValue, receiverMaxValue, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create synthetic contract log when contract parent is null and old HAPI version")
    void createWhenContractParentIsNull() {
        recordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(recordItem.getTransactionHash());
    }

    @Test
    @DisplayName("Should create synthetic contract log with a parent that has no contract result and old HAPI version")
    void createWithContractWithNoParentLogs() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(Builder::clearContractCallResult)
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(recordItem.getTransactionHash());
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
        recordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        final var capturedLog = contractLogCaptor.getValue();

        assertThat(capturedLog.getBloom()).isEqualTo(SyntheticContractLogServiceImpl.CONTRACT_LOG_MARKER);
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
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();
        long parentTimestamp = parentRecordItem.getConsensusTimestamp();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentTimestamp / 1_000_000_000)
                        .setNanos((int) (parentTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        assertThat(recordItem.getConsensusTimestamp()).isNotEqualTo(parentTimestamp);

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        var capturedLog = contractLogCaptor.getValue();

        assertThat(capturedLog.getConsensusTimestamp()).isEqualTo(parentTimestamp);
    }

    @Test
    @DisplayName("Should use child's consensus timestamp when no parent with contract result")
    void useChildConsensusTimestampWhenNoParentWithContractResult() {
        var childTimestamp = recordItem.getConsensusTimestamp();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        var capturedLog = contractLogCaptor.getValue();

        assertThat(capturedLog.getConsensusTimestamp()).isEqualTo(childTimestamp);
    }

    @Test
    @DisplayName("Should use parent's transaction hash when record has contract related parent")
    void useParentTransactionHashWhenContractRelatedParent() {
        byte[] parentHash = new byte[32];
        byte[] childHash = new byte[32];
        Arrays.fill(parentHash, (byte) 0x3a);
        Arrays.fill(childHash, (byte) 0x7b);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setTransactionHash(ByteString.copyFrom(parentHash)))
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(b -> b.setTransactionHash(ByteString.copyFrom(childHash))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                                .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        assertThat(recordItem.getContractRelatedParent()).isNotNull();
        assertThat(recordItem.getTransactionHash()).isEqualTo(childHash);
        assertThat(recordItem.getContractRelatedParent().getTransactionHash()).isEqualTo(parentHash);

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(parentHash);
    }

    @Test
    @DisplayName("Should use record item's own transaction hash when no contract related parent")
    void useOwnTransactionHashWhenNoContractRelatedParent() {
        byte[] recordHash = new byte[32];
        Arrays.fill(recordHash, (byte) 0x55);

        recordItem = recordItemBuilder
                .tokenMint(TokenType.FUNGIBLE_COMMON)
                .record(r -> r.setTransactionHash(ByteString.copyFrom(recordHash)))
                .build();

        var tokenId = recordItem.getTransactionBody().getTokenMint().getToken();
        entityTokenId = EntityId.of(tokenId);
        receiverId =
                EntityId.of(recordItem.getTransactionBody().getTransactionID().getAccountID());
        amount = recordItem.getTransactionBody().getTokenMint().getAmount();

        assertThat(recordItem.getContractRelatedParent()).isNull();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(recordHash);
    }

    @Test
    @DisplayName(
            "CryptoTransfer child with zero-padded token transfer aliases: skip synthetic log for new HAPI version")
    void skipSyntheticLogForNewHapiVersionWithZeroPaddedEvmAliases() {
        TokenID token = recordItemBuilder.tokenId();
        var senderEntityId = EntityId.of(0, 0, 6001);
        var receiverEntityId = EntityId.of(0, 0, 6002);
        var senderBytes = entityIdToBytes(senderEntityId);
        var receiverBytes = entityIdToBytes(receiverEntityId);
        long transferAmount = 888L;
        entityTokenId = EntityId.of(token);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        long parentTs = parentRecordItem.getConsensusTimestamp();
        var parentTsProto = Timestamp.newBuilder()
                .setSeconds(parentTs / 1_000_000_000L)
                .setNanos((int) (parentTs % 1_000_000_000L));

        var transferList = fungibleTokenTransferList(
                token,
                tokenTransferWithZeroPaddedEvmAlias(senderBytes, -transferAmount),
                tokenTransferWithZeroPaddedEvmAlias(receiverBytes, transferAmount));

        recordItem = recordItemBuilder
                .cryptoTransfer(TransferType.TOKEN)
                .transactionBody(tb -> tb.clearTokenTransfers().addTokenTransfers(transferList))
                .record(r -> r.clearTokenTransferLists().addTokenTransferLists(transferList))
                .record(r -> r.setContractCallResult(ContractFunctionResult.newBuilder()
                        .setContractID(HTS_PRECOMPILE_CONTRACT_ADDRESS)
                        .build()))
                .record(r -> r.setParentConsensusTimestamp(parentTsProto))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        assertThat(recordItem.getContractRelatedParent()).isSameAs(parentRecordItem);

        var childTransfers =
                recordItem.getTransactionRecord().getTokenTransferLists(0).getTransfersList();
        assertThat(childTransfers).hasSize(2);
        assertThat(childTransfers.get(0).getAccountID().getAlias().size()).isEqualTo(32);
        assertThat(childTransfers.get(0).getAccountID().getAlias().byteAt(0)).isZero();
        assertThat(childTransfers.get(1).getAccountID().getAlias().size()).isEqualTo(32);
        assertThat(childTransfers.get(1).getAccountID().getAlias().byteAt(0)).isZero();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderEntityId, receiverEntityId, transferAmount));

        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create synthetic log for old HAPI version with hook-related transaction")
    void createSyntheticLogForOldHapiVersionWithHookRelatedTransaction() {
        var topLevelRecordItem = recordItemBuilder
                .cryptoTransfer()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();
        var topLevelTimestamp = topLevelRecordItem.getConsensusTimestamp();

        var parentContractCallTimestamp = topLevelTimestamp + 1;
        var parentContractCallRecordItem = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(1))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HOOK_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentContractCallTimestamp / 1_000_000_000)
                                .setNanos((int) (parentContractCallTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) topLevelTimestamp % 1_000_000_000)))
                .recordItem(r -> r.previous(topLevelRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        var childTimestamp = parentContractCallTimestamp + 1;
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
                .recordItem(r -> r.previous(parentContractCallRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash())
                .isEqualTo(parentContractCallRecordItem.getTransactionHash());
    }

    @Test
    @DisplayName("Should skip synthetic log for new HAPI version with hook-related transaction")
    void skipSyntheticLogForNewHapiVersionWithHookRelatedTransaction() {
        var topLevelRecordItem = recordItemBuilder
                .cryptoTransfer()
                .recordItem(r -> r.hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();
        var topLevelTimestamp = topLevelRecordItem.getConsensusTimestamp();

        var parentContractCallTimestamp = topLevelTimestamp + 1;
        var parentContractCallRecordItem = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(1))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HOOK_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentContractCallTimestamp / 1_000_000_000)
                                .setNanos((int) (parentContractCallTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) topLevelTimestamp % 1_000_000_000)))
                .recordItem(r -> r.previous(topLevelRecordItem).hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        var childTimestamp = parentContractCallTimestamp + 1;
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
                .recordItem(r -> r.previous(parentContractCallRecordItem).hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create all synthetic logs for old HAPI version with three nested precompile children")
    void createAllSyntheticLogsForOldHapiVersionWithThreePrecompileChildren() {
        var topLevelRecordItem = recordItemBuilder
                .cryptoTransfer()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();
        var topLevelTimestamp = topLevelRecordItem.getConsensusTimestamp();

        var parentContractCallTimestamp = topLevelTimestamp + 1;
        var parentContractCallRecordItem = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(1))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HOOK_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentContractCallTimestamp / 1_000_000_000)
                                .setNanos((int) (parentContractCallTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) (topLevelTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(topLevelRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

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
                .recordItem(r -> r.previous(parentContractCallRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

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
                .recordItem(r -> r.previous(child1RecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

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
                .recordItem(r -> r.previous(child2RecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(child1RecordItem, entityTokenId, senderId, receiverId, amount));
        syntheticContractLogService.create(
                new TransferContractLog(child2RecordItem, entityTokenId, senderId, receiverId, amount));
        syntheticContractLogService.create(
                new TransferContractLog(child3RecordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener, times(3)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create synthetic log for old HAPI version with multiple ContractCalls as children")
    void createSyntheticLogForOldHapiVersionWithMultipleCallChildRecords() {
        var topLevelRecordItem = recordItemBuilder
                .cryptoTransfer()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();
        var topLevelTimestamp = topLevelRecordItem.getConsensusTimestamp();

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
                .recordItem(r -> r.previous(topLevelRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        var secondChildContractCallTimestamp = firstChildContractCallTimestamp + 1;

        var secondChildContractCallRecordItem = recordItemBuilder
                .contractCall(HOOK_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(2))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HOOK_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(secondChildContractCallTimestamp / 1_000_000_000)
                                .setNanos((int) (secondChildContractCallTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(topLevelTimestamp / 1_000_000_000)
                                .setNanos((int) (topLevelTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(firstChildContractCallRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(firstChildContractCallRecordItem, entityTokenId, senderId, receiverId, amount));
        syntheticContractLogService.create(new TransferContractLog(
                secondChildContractCallRecordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener, times(2)).onContractLog(contractLogCaptor.capture());
        var capturedLogs = contractLogCaptor.getAllValues();

        assertThat(capturedLogs.get(0).getTransactionHash())
                .isEqualTo(firstChildContractCallRecordItem.getTransactionHash());
        assertThat(capturedLogs.get(1).getTransactionHash())
                .isEqualTo(secondChildContractCallRecordItem.getTransactionHash());
    }

    @Test
    @DisplayName("Should not create for non-TransferContractLog types in contract transactions")
    void doNotCreateForNonTransferContractLogInContractTransaction() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        var senderEntityId = EntityId.EMPTY;
        var receiverEntityId =
                EntityId.of(recordItem.getTransactionBody().getTransactionID().getAccountID());
        syntheticContractLogService.create(
                new TransferIndexedContractLog(recordItem, entityTokenId, senderEntityId, receiverEntityId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create all identical synthetic logs for old HAPI version")
    void createAllIdenticalSyntheticLogsForOldHapiVersion() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        var transferLog = new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount);

        syntheticContractLogService.create(transferLog);
        syntheticContractLogService.create(transferLog);

        verify(entityListener, times(2)).onContractLog(any());
    }

    @Test
    @DisplayName("Should skip all synthetic logs for new HAPI version regardless of log content")
    void skipAllSyntheticLogsForNewHapiVersion() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        var transferLog = new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount);

        syntheticContractLogService.create(transferLog);
        syntheticContractLogService.create(transferLog);

        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should skip synthetic log for new HAPI version even within same RecordItem")
    void skipSyntheticLogForNewHapiVersionWithinSameRecordItem() {
        recordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        var transferLog = new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount);

        syntheticContractLogService.create(transferLog);

        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create all three identical synthetic logs for old HAPI version")
    void createAllThreeIdenticalLogsForOldHapiVersion() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        var transferLog = new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount);

        syntheticContractLogService.create(transferLog);
        syntheticContractLogService.create(transferLog);
        syntheticContractLogService.create(transferLog);

        verify(entityListener, times(3)).onContractLog(any());
    }

    @Test
    @DisplayName("Should skip all synthetic logs for new HAPI version even with different log contents")
    void skipAllDifferentSyntheticLogsForNewHapiVersion() {
        var secondReceiverId = EntityId.of(0, 0, 999);
        var secondAmount = 500L;

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(HAPI_SYNTHETIC_LOG_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, secondReceiverId, secondAmount));

        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName("Should create all different synthetic logs for old HAPI version")
    void createAllDifferentSyntheticLogsForOldHapiVersion() {
        var secondReceiverId = EntityId.of(0, 0, 999);
        var secondAmount = 500L;

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        recordItem = recordItemBuilder
                .contractCall()
                .record(r -> r.setParentConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(parentRecordItem.getConsensusTimestamp() / 1_000_000_000)
                        .setNanos((int) (parentRecordItem.getConsensusTimestamp() % 1_000_000_000))))
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, secondReceiverId, secondAmount));

        verify(entityListener, times(2)).onContractLog(any());
    }

    @Test
    @DisplayName(
            "Should not create synthetic log when multi-party transfer is disabled and child has more than 2 token transfer lists")
    void doNotCreateWhenMultiPartyDisabledAndChildHasMoreThanTwoTokenTransferLists() {
        entityProperties.getPersist().setSyntheticContractLogsMulti(false);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

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
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName(
            "Should create synthetic log when multi-party transfer is enabled and child has more than 2 token transfer lists")
    void createWhenMultiPartyEnabledAndChildHasMoreThanTwoTokenTransferLists() {
        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

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
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(parentRecordItem.getTransactionHash());
    }

    @Test
    @DisplayName(
            "Should create synthetic log when multi-party transfer is disabled but child has exactly 2 token transfer lists")
    void createWhenMultiPartyDisabledAndChildHasExactlyTwoTokenTransferLists() {
        entityProperties.getPersist().setSyntheticContractLogsMulti(false);

        var parentRecordItem = recordItemBuilder
                .contractCall()
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

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
                .recordItem(r -> r.previous(parentRecordItem).hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(1)).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(parentRecordItem.getTransactionHash());
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
                .recordItem(r -> r.hapiVersion(OLD_HAPI_VERSION))
                .build();

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));
        verify(entityListener, times(0)).onContractLog(any());
    }

    @Test
    @DisplayName(
            "Should use EthereumTransaction hash for synthetic log when parent is EthereumTransaction with ContractCall and CryptoTransfer children")
    void useEthereumTransactionHashForSyntheticLogWithContractCallAndCryptoTransferChildren() {
        var hapiVersion069 = new Version(0, 69, 0);
        byte[] ethereumHash = new byte[32];
        Arrays.fill(ethereumHash, (byte) 0xAB);

        var ethereumTransaction = EthereumTransaction.builder()
                .consensusTimestamp(System.nanoTime())
                .hash(ethereumHash)
                .payerAccountId(EntityId.of(0, 0, 1000))
                .build();

        var parentEthereumTxRecordItem = recordItemBuilder
                .ethereumTransaction()
                .record(r -> r.setEthereumHash(ByteString.copyFrom(ethereumHash)))
                .recordItem(r -> r.hapiVersion(hapiVersion069))
                .build();
        parentEthereumTxRecordItem.setEthereumTransaction(ethereumTransaction);

        var parentTimestamp = parentEthereumTxRecordItem.getConsensusTimestamp();

        var childContractCallTimestamp = parentTimestamp + 1;
        var childContractCallRecordItem = recordItemBuilder
                .contractCall(HTS_PRECOMPILE_CONTRACT_ADDRESS)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(1))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HTS_PRECOMPILE_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(childContractCallTimestamp / 1_000_000_000)
                                .setNanos((int) (childContractCallTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentTimestamp / 1_000_000_000)
                                .setNanos((int) (parentTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(parentEthereumTxRecordItem).hapiVersion(hapiVersion069))
                .build();

        var childCryptoTransferTimestamp = childContractCallTimestamp + 1;
        recordItem = recordItemBuilder
                .cryptoTransfer(TransferType.TOKEN)
                .record(r -> r.setTransactionID(r.getTransactionID().toBuilder().setNonce(2))
                        .setContractCallResult(
                                ContractFunctionResult.newBuilder().setContractID(HTS_PRECOMPILE_CONTRACT_ADDRESS))
                        .setConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(childCryptoTransferTimestamp / 1_000_000_000)
                                .setNanos((int) (childCryptoTransferTimestamp % 1_000_000_000)))
                        .setParentConsensusTimestamp(Timestamp.newBuilder()
                                .setSeconds(parentTimestamp / 1_000_000_000)
                                .setNanos((int) (parentTimestamp % 1_000_000_000))))
                .recordItem(r -> r.previous(childContractCallRecordItem).hapiVersion(hapiVersion069))
                .build();

        assertThat(recordItem.getContractRelatedParent()).isSameAs(parentEthereumTxRecordItem);
        assertThat(parentEthereumTxRecordItem.getTransactionHash()).isEqualTo(ethereumHash);

        syntheticContractLogService.create(
                new TransferContractLog(recordItem, entityTokenId, senderId, receiverId, amount));

        verify(entityListener).onContractLog(contractLogCaptor.capture());
        assertThat(contractLogCaptor.getValue().getTransactionHash()).isEqualTo(ethereumHash);
    }

    private static AccountAmount tokenTransferWithZeroPaddedEvmAlias(byte[] evmAddress, long amount) {
        byte[] padded = new byte[32];
        if (evmAddress != null && evmAddress.length > 0) {
            System.arraycopy(evmAddress, 0, padded, 32 - evmAddress.length, evmAddress.length);
        }
        return AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder().setAlias(ByteString.copyFrom(padded)))
                .setAmount(amount)
                .build();
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

    private byte[] entityIdToBytes(EntityId entityId) {
        if (EntityId.isEmpty(entityId)) {
            return new byte[0];
        }
        return DomainUtils.trim(DomainUtils.toEvmAddress(entityId));
    }
}
