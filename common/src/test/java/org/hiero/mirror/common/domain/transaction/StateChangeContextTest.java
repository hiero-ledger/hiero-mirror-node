// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_ACCOUNTS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_BYTECODE_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_FILES_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_NFTS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_NODES_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_ROSTER_STATE_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOPICS_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.StateChangeContext.EMPTY_CONTEXT;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.bytes;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.contractStorageMapDeleteChange;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.contractStorageMapUpdateChange;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getAccountId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getContractId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getFileId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getTokenId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getTopicId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.makeIdentical;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.UInt64Value;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.NewStateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.platform.state.legacy.NodeId;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AccountPendingAirdrop;
import com.hederahashgraph.api.proto.java.Bytecode;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.File;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Node;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.SlotKey;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.Topic;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.CommonUtils;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;

final class StateChangeContextTest {

    @Test
    void accounts() {
        // given
        var accountId1 = getAccountId();
        var accountId2 = getAccountId();
        var accountId3 = getAccountId();
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(accountMapUpdateChange(accountId1))
                .addStateChanges(accountMapUpdateChange(accountId2))
                .addStateChanges(otherMapUpdateChange())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.getAccount(accountId1)).get().returns(accountId1, Account::getAccountId);
        assertThat(context.getAccount(accountId2)).get().returns(accountId2, Account::getAccountId);
        assertThat(context.getAccount(accountId3)).isEmpty();
    }

    @Test
    void contractId() {
        // given
        var contractId1 = getAccountId();
        var evmAddress1 = evmAddress();
        var contractId2 = getAccountId();
        var evmAddress2 = evmAddress();
        var contractId3 = getAccountId();
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(accountIdMapUpdateChange())
                .addStateChanges(accountMapUpdateChange())
                .addStateChanges(contractMapUpdateChange(contractId1, evmAddress1))
                .addStateChanges(contractMapUpdateChange(contractId2, evmAddress2))
                .addStateChanges(contractMapUpdateChange(contractId3, ByteString.EMPTY))
                .addStateChanges(otherMapUpdateChange())
                .addStateChanges(rosterStateChange())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.getContractId(evmAddress1))
                .get()
                .returns(contractId1.getShardNum(), ContractID::getShardNum)
                .returns(contractId1.getRealmNum(), ContractID::getRealmNum)
                .returns(contractId1.getAccountNum(), ContractID::getContractNum)
                .returns(false, ContractID::hasEvmAddress);
        assertThat(context.getContractId(evmAddress2))
                .get()
                .returns(contractId2.getShardNum(), ContractID::getShardNum)
                .returns(contractId2.getRealmNum(), ContractID::getRealmNum)
                .returns(contractId2.getAccountNum(), ContractID::getContractNum)
                .returns(false, ContractID::hasEvmAddress);
        assertThat(context.getContractId(evmAddress())).isEmpty();
    }

    @Test
    void contractBytecode() {
        // given
        var contractId = getContractId();
        var bytecode = bytes(128);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_BYTECODE_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setContractIdKey(contractId))
                                .setValue(MapChangeValue.newBuilder()
                                        .setBytecodeValue(Bytecode.newBuilder().setCode(bytecode)))))
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.getContractBytecode(contractId)).contains(bytecode);
        assertThat(context.getContractBytecode(getContractId())).isEmpty();
    }

    @Test
    void contractStorageChangeByIdAndIndex() {
        // given
        var contractIdA = getContractId();
        var contractASlot1 = bytes(32);
        var contractASlot1Value = bytes(4);
        var contractASlot2 = bytes(32);
        var contractIdB = getContractId();
        var contractBSlot1 = bytes(32);
        var contractBSlot1Value = bytes(8);
        var contractBSlot2 = bytes(32);
        var contractBSlot2Value = bytes(12);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(contractStorageMapUpdateChange(contractIdA, contractASlot1, contractASlot1Value))
                .addStateChanges(contractStorageMapDeleteChange(contractIdA, contractASlot2))
                .addStateChanges(contractStorageMapUpdateChange(contractIdB, contractBSlot1, contractBSlot1Value))
                // an identical MapUpdateChange
                .addStateChanges(
                        makeIdentical(contractStorageMapUpdateChange(contractIdB, contractBSlot2, contractBSlot2Value)))
                .addStateChanges(otherMapUpdateChange())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.getContractStorageChange(contractIdA, 0))
                .isEqualTo(new StateChangeContext.SlotValue(contractASlot1, BytesValue.of(contractASlot1Value)));
        assertThat(context.getContractStorageChange(contractIdA, 1))
                .isEqualTo(new StateChangeContext.SlotValue(contractASlot2, BytesValue.getDefaultInstance()));
        assertThat(context.getContractStorageChange(contractIdB, 0))
                .isEqualTo(new StateChangeContext.SlotValue(contractBSlot1, BytesValue.of(contractBSlot1Value)));
        assertThat(context.getContractStorageChange(contractIdA, -1)).isNull();
        assertThat(context.getContractStorageChange(contractIdB, 1)).isNull();
        assertThat(context.getContractStorageChange(getContractId(), 0)).isNull();
    }

    @Test
    void getContractStorageValueWritten() {
        // given
        var contractIdA = getContractId();
        var contractASlot1 = bytes(24);
        var contractASlot1Padded =
                DomainUtils.fromBytes(Bytes.concat(new byte[8], DomainUtils.toBytes(contractASlot1)));
        var contractASlot1Value = bytes(4);
        var contractASlot2 = bytes(32);
        var contractIdB = getContractId();
        var contractBSlot1 = bytes(32);
        var contractBSlot1Value = bytes(8);
        var contractBSlot2 = bytes(32);
        var contractBSlot2Value = bytes(12);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(contractStorageMapUpdateChange(contractIdA, contractASlot1Padded, contractASlot1Value))
                .addStateChanges(contractStorageMapDeleteChange(contractIdA, contractASlot2))
                .addStateChanges(contractStorageMapUpdateChange(contractIdB, contractBSlot1, contractBSlot1Value))
                // an identical MapUpdateChange
                .addStateChanges(
                        makeIdentical(contractStorageMapUpdateChange(contractIdB, contractBSlot2, contractBSlot2Value)))
                .addStateChanges(otherMapUpdateChange())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));

        // then
        var contractASlotKeyBuilder = SlotKey.newBuilder().setContractID(contractIdA);
        assertThat(context.getContractStorageValueWritten(
                        contractASlotKeyBuilder.setKey(contractASlot1).build()))
                .isEqualTo(BytesValue.of(contractASlot1Value));
        assertThat(context.getContractStorageValueWritten(
                        contractASlotKeyBuilder.setKey(contractASlot1Padded).build()))
                .isEqualTo(BytesValue.of(contractASlot1Value));
        assertThat(context.getContractStorageValueWritten(
                        contractASlotKeyBuilder.setKey(contractASlot2).build()))
                .isEqualTo(BytesValue.getDefaultInstance());
        assertThat(context.getContractStorageValueWritten(
                        contractASlotKeyBuilder.setKey(bytes(32)).build()))
                .isNull();
        var contractBSlotKeyBuilder = SlotKey.newBuilder().setContractID(contractIdB);
        assertThat(context.getContractStorageValueWritten(
                        contractBSlotKeyBuilder.setKey(contractBSlot1).build()))
                .isEqualTo(BytesValue.of(contractBSlot1Value));
        assertThat(context.getContractStorageValueWritten(
                        contractBSlotKeyBuilder.setKey(contractBSlot2).build()))
                .isNull();
    }

    @Test
    void emptyContext() {
        assertThat(EMPTY_CONTEXT.getContractId(evmAddress())).isEmpty();
        assertThat(EMPTY_CONTEXT.getNewFileId()).isEmpty();
        assertThat(EMPTY_CONTEXT.getNewNodeId()).isEmpty();
        assertThat(EMPTY_CONTEXT.getNewTokenId()).isEmpty();
        assertThat(EMPTY_CONTEXT.getNewTopicId()).isEmpty();
        assertThat(EMPTY_CONTEXT.getTopicMessage(getTopicId())).isEmpty();
        assertThat(EMPTY_CONTEXT.trackPendingFungibleAirdrop(getPendingAirdropId(), 10))
                .isEmpty();
        assertThat(EMPTY_CONTEXT.trackTokenTotalSupply(getTokenId(), 10)).isEmpty();
    }

    @Test
    void fileId() {
        // given
        var fileId1 = getFileId();
        var fileId2 = getFileId();
        var file1StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setFileIdKey(fileId1))
                .setValue(MapChangeValue.newBuilder()
                        .setFileValue(File.newBuilder().setFileId(fileId1)))
                .build();
        var file2StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setFileIdKey(fileId2))
                .setValue(MapChangeValue.newBuilder()
                        .setFileValue(File.newBuilder().setFileId(fileId2)))
                .build();
        var template = StateChange.newBuilder().setStateId(STATE_ID_FILES_VALUE);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(template.setMapUpdate(file1StateChange).build())
                .addStateChanges(template.setMapUpdate(file2StateChange).build())
                .addStateChanges(otherMapUpdateChange())
                .addStateChanges(rosterStateChange())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));
        var actual = List.of(context.getNewFileId(), context.getNewFileId(), context.getNewFileId());

        // then
        assertThat(actual).containsExactly(Optional.of(fileId2), Optional.of(fileId1), Optional.empty());
    }

    @Test
    void nodeId() {
        // given
        var node1StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setEntityNumberKey(UInt64Value.of(2L)))
                .setValue(MapChangeValue.newBuilder()
                        .setNodeValue(Node.newBuilder().setNodeId(2L)));
        var node2StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setEntityNumberKey(UInt64Value.of(3L)))
                .setValue(MapChangeValue.newBuilder()
                        .setNodeValue(Node.newBuilder().setNodeId(3L)));
        var otherStateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder()
                        .setNodeIdKey(NodeId.newBuilder().setId(1L)))
                .setValue(MapChangeValue.newBuilder()
                        .setNodeValue(Node.newBuilder().setNodeId(1L)))
                .build();
        var template = StateChange.newBuilder().setStateId(STATE_ID_NODES_VALUE);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(template.setMapUpdate(node1StateChange).build())
                .addStateChanges(template.setMapUpdate(node2StateChange).build())
                .addStateChanges(template.setMapUpdate(otherStateChange).build())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));
        var actual = List.of(context.getNewNodeId(), context.getNewNodeId(), context.getNewNodeId());

        // then
        assertThat(actual).containsExactly(Optional.of(3L), Optional.of(2L), Optional.empty());
    }

    @Test
    void pendingFungibleAirdrop() {
        // given
        var pendingAirdropId = getPendingAirdropId();
        var nftPendingAirdropId = getPendingAirdropId().toBuilder()
                .clearFungibleTokenType()
                .setNonFungibleToken(NftID.newBuilder().setTokenID(getTokenId()).setSerialNumber(1))
                .build();
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_PENDING_AIRDROPS_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setPendingAirdropIdKey(pendingAirdropId))
                                .setValue(MapChangeValue.newBuilder()
                                        .setAccountPendingAirdropValue(AccountPendingAirdrop.newBuilder()
                                                .setPendingAirdropValue(PendingAirdropValue.newBuilder()
                                                        .setAmount(3000L))))))
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_PENDING_AIRDROPS_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setPendingAirdropIdKey(nftPendingAirdropId))
                                .setValue(MapChangeValue.newBuilder())))
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));
        // fungible airdrop amount is tracked in reverse timestamp order, for T1 < T2 < T3
        // - at T3, airdropped an additional 1000 and the accumulated amount is 3000
        // - at T2, airdropped 2000 and the amount should be 2000
        // - at T1, airdropped 500, however there is no record in state (likely it's cancelled between T1 and T2),
        //   so should get an empty optional
        var actual = List.of(
                context.trackPendingFungibleAirdrop(pendingAirdropId, 1000L),
                context.trackPendingFungibleAirdrop(pendingAirdropId, 2000L),
                context.trackPendingFungibleAirdrop(pendingAirdropId, 500L));

        // then
        assertThat(actual).containsExactly(Optional.of(3000L), Optional.of(2000L), Optional.empty());
        assertThat(context.trackPendingFungibleAirdrop(getPendingAirdropId(), 200L))
                .isEmpty();
    }

    @Test
    void token() {
        // given
        var tokenId1 = getTokenId();
        var tokenId2 = getTokenId();
        var token1StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId1))
                .setValue(MapChangeValue.newBuilder()
                        .setTokenValue(Token.newBuilder().setTokenId(tokenId1).setTotalSupply(3000L)))
                .build();
        var token2StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId2))
                .setValue(MapChangeValue.newBuilder()
                        .setTokenValue(Token.newBuilder().setTokenId(tokenId2).setTotalSupply(5000L)))
                .build();
        var template = StateChange.newBuilder().setStateId(STATE_ID_TOKENS_VALUE);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(template.setMapUpdate(token1StateChange).build())
                .addStateChanges(template.setMapUpdate(token2StateChange).build())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));
        var newTokenIds = List.of(context.getNewTokenId(), context.getNewTokenId(), context.getNewTokenId());
        var token1TotalSupplies =
                List.of(context.trackTokenTotalSupply(tokenId1, 1000L), context.trackTokenTotalSupply(tokenId1, -500L));
        var token2TotalSupplies =
                List.of(context.trackTokenTotalSupply(tokenId2, -2000L), context.trackTokenTotalSupply(tokenId2, 800L));
        var otherTokenTotalSupplies = context.trackTokenTotalSupply(getTokenId(), 100L);

        // then
        assertThat(newTokenIds).containsExactly(Optional.of(tokenId2), Optional.of(tokenId1), Optional.empty());
        assertThat(token1TotalSupplies).containsExactly(Optional.of(3000L), Optional.of(4000L));
        assertThat(token2TotalSupplies).containsExactly(Optional.of(5000L), Optional.of(3000L));
        assertThat(otherTokenTotalSupplies).isEmpty();
    }

    @Test
    void topic() {
        // given
        var topicId1 = getTopicId();
        var topicId2 = getTopicId();
        // topic1 has no messages
        var topic1StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setTopicIdKey(topicId1))
                .setValue(MapChangeValue.newBuilder()
                        .setTopicValue(Topic.newBuilder().setTopicId(topicId1)))
                .build();
        var topic2RunningHash = DomainUtils.fromBytes(CommonUtils.nextBytes(48));
        var topic2StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setTopicIdKey(topicId2))
                .setValue(MapChangeValue.newBuilder()
                        .setTopicValue(Topic.newBuilder()
                                .setRunningHash(topic2RunningHash)
                                .setSequenceNumber(2)
                                .setTopicId(topicId2)))
                .build();
        var template = StateChange.newBuilder().setStateId(STATE_ID_TOPICS_VALUE);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(template.setMapUpdate(topic1StateChange).build())
                .addStateChanges(template.setMapUpdate(topic2StateChange).build())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));
        var topicIds = List.of(context.getNewTopicId(), context.getNewTopicId(), context.getNewTopicId());
        var topic1Message = context.getTopicMessage(topicId1);
        var topic2Messages = List.of(context.getTopicMessage(topicId2), context.getTopicMessage(topicId2));

        // then
        var expectedTopic2Messages = List.of(
                Optional.of(TopicMessage.builder()
                        .runningHash(DomainUtils.toBytes(topic2RunningHash))
                        .sequenceNumber(2L)
                        .build()),
                Optional.<TopicMessage>empty());
        assertThat(topicIds).containsExactly(Optional.of(topicId2), Optional.of(topicId1), Optional.empty());
        assertThat(topic1Message).isEmpty();
        assertThat(topic2Messages).containsExactlyElementsOf(expectedTopic2Messages);
    }

    private ByteString evmAddress() {
        return DomainUtils.fromBytes(CommonUtils.nextBytes(20));
    }

    private PendingAirdropId getPendingAirdropId() {
        return PendingAirdropId.newBuilder()
                .setReceiverId(getAccountId())
                .setSenderId(getAccountId())
                .setFungibleTokenType(getTokenId())
                .build();
    }

    private StateChange accountIdMapUpdateChange() {
        var accountId = getAccountId();
        return StateChange.newBuilder()
                .setStateId(STATE_ID_ACCOUNTS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                        .setValue(MapChangeValue.newBuilder().setAccountIdValue(accountId))
                        .build())
                .build();
    }

    private StateChange accountMapUpdateChange(AccountID accountId) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_ACCOUNTS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                        .setValue(MapChangeValue.newBuilder()
                                .setAccountValue(Account.newBuilder().setAccountId(accountId)))
                        .build())
                .build();
    }

    private StateChange accountMapUpdateChange() {
        return accountMapUpdateChange(getAccountId());
    }

    private StateChange contractMapUpdateChange(AccountID accountId, ByteString evmAddress) {
        var account = Account.newBuilder()
                .setAccountId(accountId)
                .setAlias(evmAddress)
                .setSmartContract(true)
                .build();
        return StateChange.newBuilder()
                .setStateId(STATE_ID_ACCOUNTS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                        .setValue(MapChangeValue.newBuilder().setAccountValue(account))
                        .build())
                .build();
    }

    private StateChange otherMapUpdateChange() {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_NFTS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .build();
    }

    private StateChange rosterStateChange() {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_ROSTER_STATE_VALUE)
                .setStateAdd(NewStateChange.newBuilder().build())
                .build();
    }
}
