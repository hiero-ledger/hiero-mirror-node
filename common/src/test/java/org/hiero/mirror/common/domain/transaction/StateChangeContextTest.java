// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_ACCOUNTS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_BYTECODE_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_FILES_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_NFTS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_NODES_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_REGISTERED_NODES_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_ROSTER_STATE_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS_VALUE;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOPICS_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.domain.transaction.StateChangeContext.EMPTY_CONTEXT;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.bytes;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.contractStorageMapDeleteChange;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.contractStorageMapUpdateChange;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.convert;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getAccountId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getContractId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getEvmHookSlotKey;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getFileId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getTokenId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.getTopicId;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.hookStorageMapDeleteChange;
import static org.hiero.mirror.common.domain.transaction.StateChangeTestUtils.hookStorageMapUpdateChange;
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
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.File;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Node;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.RegisteredNode;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Topic;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.CommonUtils;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class StateChangeContextTest {

    @Test
    void accounts() {
        // given
        final var accountId1 = getAccountId();
        final var account1Alias = alias();
        final var accountId2 = getAccountId();
        final var account2Alias = alias();
        final var accountId3 = getAccountId();
        final var noAlias = getAccountId();
        final var stateChanges = StateChanges.newBuilder()
                .addStateChanges(accountMapUpdateChange(accountId1, account1Alias))
                .addStateChanges(accountMapUpdateChange(accountId2, account2Alias))
                .addStateChanges(accountMapUpdateChange(noAlias, ByteString.EMPTY))
                .addStateChanges(otherMapUpdateChange())
                .build();

        // when
        final var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.getAccountAlias(accountId1)).isEqualTo(account1Alias);
        assertThat(context.getAccountAlias(accountId2)).isEqualTo(account2Alias);
        assertThat(context.getAccountAlias(accountId3)).isNull();
        assertThat(context.getAccountAlias(noAlias)).isNull();
        assertThat(context.getAccountEthereumNonce(accountId1)).isZero();
        assertThat(context.getAccountEthereumNonce(noAlias)).isZero();
        assertThat(context.getAccountEthereumNonce(accountId3)).isNull();
    }

    @Test
    void accountsPartial() {
        // given
        final var nonceChanged = getAccountId();
        final var nonceCleared = getAccountId();
        final var aliasChanged = getAccountId();
        final var aliasChangedAlias = alias();
        final var aliasCleared = getAccountId();
        final var stateChanges = StateChanges.newBuilder()
                .addStateChanges(partialAccountMapUpdateChange(
                        nonceChanged, Account.newBuilder().setEthereumNonce(5)))
                .addStateChanges(partialAccountMapUpdateChange(
                        nonceCleared, Account.newBuilder(), Account.ETHEREUM_NONCE_FIELD_NUMBER))
                .addStateChanges(partialAccountMapUpdateChange(
                        aliasChanged, Account.newBuilder().setAlias(aliasChangedAlias)))
                .addStateChanges(
                        partialAccountMapUpdateChange(aliasCleared, Account.newBuilder(), Account.ALIAS_FIELD_NUMBER))
                .build();

        // when
        final var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.getAccountAlias(nonceChanged)).isNull();
        assertThat(context.getAccountEthereumNonce(nonceChanged)).isEqualTo(5L);
        assertThat(context.getAccountAlias(nonceCleared)).isNull();
        assertThat(context.getAccountEthereumNonce(nonceCleared)).isZero();
        assertThat(context.getAccountAlias(aliasChanged)).isEqualTo(aliasChangedAlias);
        assertThat(context.getAccountEthereumNonce(aliasChanged)).isNull();
        assertThat(context.getAccountAlias(aliasCleared)).isNull();
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
        assertThat(context.getContractBytecode(contractId)).isEqualTo(bytecode);
        assertThat(context.getContractBytecode(getContractId())).isNull();
    }

    @Test
    void contractId() {
        // given
        final var contract = getAccountId();
        final var contractAddress = evmAddress();
        final var created = getAccountId();
        final var createdAddress = evmAddress();
        final var account = getAccountId();
        final var accountAddress = evmAddress();
        final var partialContract = getAccountId();
        final var partialContractAddress = evmAddress();
        final var partialUnknown = getAccountId();
        final var partialUnknownAddress = evmAddress();
        final var stateChanges = StateChanges.newBuilder()
                .addStateChanges(accountMapUpdateChange(
                        contract, Account.newBuilder().setAlias(contractAddress).setSmartContract(true)))
                .addStateChanges(accountMapUpdateChange(
                        created, Account.newBuilder().setAlias(createdAddress).setSmartContract(true)))
                .addStateChanges(
                        accountMapUpdateChange(account, Account.newBuilder().setAlias(accountAddress)))
                .addStateChanges(accountMapUpdateChange(
                        getAccountId(), Account.newBuilder().setDeleted(true).setSmartContract(true)))
                .addStateChanges(partialAccountMapUpdateChange(
                        partialContract,
                        Account.newBuilder().setAlias(partialContractAddress).setSmartContract(true)))
                .addStateChanges(partialAccountMapUpdateChange(
                        partialUnknown, Account.newBuilder().setAlias(partialUnknownAddress)))
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_BYTECODE_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setContractIdKey(toContractId(created)))
                                .setValue(MapChangeValue.newBuilder()
                                        .setBytecodeValue(Bytecode.newBuilder().setCode(bytes(128))))))
                .build();

        // when
        final var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.getContractId(contractAddress)).isEqualTo(toContractId(contract));
        assertThat(context.getContractId(partialContractAddress)).isEqualTo(toContractId(partialContract));
        // The address may be reused within the transactional unit after a delete
        assertThat(context.getContractId(createdAddress)).isNull();
        assertThat(context.getContractId(accountAddress)).isNull();
        assertThat(context.getContractId(partialUnknownAddress)).isNull();
        assertThat(context.getContractId(ByteString.EMPTY)).isNull();
        assertThat(context.getContractId(evmAddress())).isNull();
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
        var slotIdA = ContractSlotId.of(contractIdA, null);
        var slotIdB = ContractSlotId.of(contractIdB, null);
        assertThat(context.getContractStorageChange(slotIdA, 0))
                .isEqualTo(new StateChangeContext.SlotValue(contractASlot1, BytesValue.of(contractASlot1Value)));
        assertThat(context.getContractStorageChange(slotIdA, 1))
                .isEqualTo(new StateChangeContext.SlotValue(contractASlot2, BytesValue.getDefaultInstance()));
        assertThat(context.getContractStorageChange(slotIdB, 0))
                .isEqualTo(new StateChangeContext.SlotValue(contractBSlot1, BytesValue.of(contractBSlot1Value)));
        assertThat(context.getContractStorageChange(slotIdA, -1)).isNull();
        assertThat(context.getContractStorageChange(slotIdB, 1)).isNull();
        var otherSlotId = ContractSlotId.of(getContractId(), null);
        assertThat(context.getContractStorageChange(otherSlotId, 0)).isNull();
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
        var slotIdA = ContractSlotId.of(contractIdA, null);
        var contractASlotKeyBuilder = new ContractSlotKey(slotIdA, null);
        assertThat(context.getContractStorageValueWritten(new ContractSlotKey(slotIdA, contractASlot1)))
                .isEqualTo(BytesValue.of(contractASlot1Value));
        assertThat(context.getContractStorageValueWritten(new ContractSlotKey(slotIdA, contractASlot1Padded)))
                .isEqualTo(BytesValue.of(contractASlot1Value));
        assertThat(context.getContractStorageValueWritten(new ContractSlotKey(slotIdA, contractASlot2)))
                .isEqualTo(BytesValue.getDefaultInstance());
        assertThat(context.getContractStorageValueWritten(new ContractSlotKey(slotIdA, bytes(32))))
                .isNull();
        var slotIdB = ContractSlotId.of(contractIdB, null);
        assertThat(context.getContractStorageValueWritten(new ContractSlotKey(slotIdB, contractBSlot1)))
                .isEqualTo(BytesValue.of(contractBSlot1Value));
        assertThat(context.getContractStorageValueWritten(new ContractSlotKey(slotIdB, contractBSlot2)))
                .isNull();
    }

    @Test
    void emptyContext() {
        assertThat(EMPTY_CONTEXT.getContractId(evmAddress())).isNull();
        assertThat(EMPTY_CONTEXT.getNewFileId()).isNull();
        assertThat(EMPTY_CONTEXT.getNewNodeId()).isNull();
        assertThat(EMPTY_CONTEXT.getNewTokenId()).isNull();
        assertThat(EMPTY_CONTEXT.getNewTopicId()).isNull();
        assertThat(EMPTY_CONTEXT.getTopicMessage(getTopicId())).isNull();
        assertThat(EMPTY_CONTEXT.trackPendingFungibleAirdrop(getPendingAirdropId(), 10))
                .isNull();
        assertThat(EMPTY_CONTEXT.trackTokenTotalSupply(getTokenId(), 10)).isNull();
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
        var actual = Arrays.asList(context.getNewFileId(), context.getNewFileId(), context.getNewFileId());

        // then
        assertThat(actual).containsExactly(fileId2, fileId1, null);
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
        var actual = Arrays.asList(context.getNewNodeId(), context.getNewNodeId(), context.getNewNodeId());

        // then
        assertThat(actual).containsExactly(3L, 2L, null);
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
        var actual = Arrays.asList(
                context.trackPendingFungibleAirdrop(pendingAirdropId, 1000L),
                context.trackPendingFungibleAirdrop(pendingAirdropId, 2000L),
                context.trackPendingFungibleAirdrop(pendingAirdropId, 500L));

        // then
        assertThat(actual).containsExactly(3000L, 2000L, null);
        assertThat(context.trackPendingFungibleAirdrop(getPendingAirdropId(), 200L))
                .isNull();
    }

    @Test
    void registeredNodeId() {
        // given
        final var registeredNodeStateChanges = LongStream.range(1, 3)
                .boxed()
                .map(id -> MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setEntityNumberKey(UInt64Value.of(id)))
                        .setValue(MapChangeValue.newBuilder()
                                .setRegisteredNodeValue(
                                        RegisteredNode.newBuilder().setRegisteredNodeId(id)))
                        .build())
                .map(mapUpdateChange -> StateChange.newBuilder()
                        .setMapUpdate(mapUpdateChange)
                        .setStateId(STATE_ID_REGISTERED_NODES_VALUE)
                        .build())
                .toList();
        final var otherStateChange = StateChange.newBuilder()
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setNodeIdKey(NodeId.newBuilder().setId(1L)))
                        .setValue(MapChangeValue.newBuilder()
                                .setNodeValue(Node.newBuilder().setNodeId(1L)))
                        .build())
                .setStateId(STATE_ID_NODES_VALUE)
                .build();
        final var stateChanges = StateChanges.newBuilder()
                .addAllStateChanges(registeredNodeStateChanges)
                .addStateChanges(otherStateChange)
                .build();

        // when
        final var context = new StateChangeContext(List.of(stateChanges));
        final var actual = Arrays.asList(
                context.getNewRegisteredNodeId(), context.getNewRegisteredNodeId(), context.getNewRegisteredNodeId());

        // then
        assertThat(actual).containsExactly(2L, 1L, null);
    }

    @Test
    void token() {
        // given
        var tokenId1 = getTokenId();
        var tokenId2 = getTokenId();
        var token1StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId1))
                .setValue(MapChangeValue.newBuilder()
                        .setTokenValue(Token.newBuilder().setTotalSupply(3000L)))
                .build();
        var token2StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId2))
                .setValue(MapChangeValue.newBuilder()
                        .setTokenValue(Token.newBuilder().setTotalSupply(5000L)))
                .build();
        var template = StateChange.newBuilder().setStateId(STATE_ID_TOKENS_VALUE);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(template.setMapUpdate(token1StateChange).build())
                .addStateChanges(template.setMapUpdate(token2StateChange).build())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));
        var newTokenIds = Arrays.asList(context.getNewTokenId(), context.getNewTokenId(), context.getNewTokenId());
        var token1TotalSupplies = Arrays.asList(
                context.trackTokenTotalSupply(tokenId1, 1000L), context.trackTokenTotalSupply(tokenId1, -500L));
        var token2TotalSupplies = Arrays.asList(
                context.trackTokenTotalSupply(tokenId2, -2000L), context.trackTokenTotalSupply(tokenId2, 800L));
        var otherTokenTotalSupplies = context.trackTokenTotalSupply(getTokenId(), 100L);

        // then
        assertThat(newTokenIds).containsExactly(tokenId2, tokenId1, null);
        assertThat(token1TotalSupplies).containsExactly(3000L, 4000L);
        assertThat(token2TotalSupplies).containsExactly(5000L, 3000L);
        assertThat(otherTokenTotalSupplies).isNull();
    }

    @Test
    void topic() {
        // given
        var topicId1 = getTopicId();
        var topicId2 = getTopicId();
        // topic1 has no messages
        var topic1StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setTopicIdKey(topicId1))
                .setValue(MapChangeValue.newBuilder().setTopicValue(Topic.newBuilder()))
                .build();
        var topic2RunningHash = DomainUtils.fromBytes(CommonUtils.nextBytes(48));
        var topic2StateChange = MapUpdateChange.newBuilder()
                .setKey(MapChangeKey.newBuilder().setTopicIdKey(topicId2))
                .setValue(MapChangeValue.newBuilder()
                        .setTopicValue(Topic.newBuilder()
                                .setRunningHash(topic2RunningHash)
                                .setSequenceNumber(2)))
                .build();
        var template = StateChange.newBuilder().setStateId(STATE_ID_TOPICS_VALUE);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(template.setMapUpdate(topic1StateChange).build())
                .addStateChanges(template.setMapUpdate(topic2StateChange).build())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));
        var topicIds = Arrays.asList(context.getNewTopicId(), context.getNewTopicId(), context.getNewTopicId());
        var topic1Message = context.getTopicMessage(topicId1);
        var topic2Messages = Arrays.asList(context.getTopicMessage(topicId2), context.getTopicMessage(topicId2));

        // then
        var expectedTopic2Messages = Arrays.asList(
                TopicMessage.builder()
                        .runningHash(DomainUtils.toBytes(topic2RunningHash))
                        .sequenceNumber(2L)
                        .build(),
                null);
        assertThat(topicIds).containsExactly(topicId2, topicId1, null);
        assertThat(topic1Message).isNull();
        assertThat(topic2Messages).containsExactlyElementsOf(expectedTopic2Messages);
    }

    @Test
    void isPresent() {
        final var full = MapUpdateChange.getDefaultInstance();
        final var partial = MapUpdateChange.newBuilder()
                .setPartial(true)
                .addClearedFields(Account.MEMO_FIELD_NUMBER)
                .build();
        final var account = Account.newBuilder()
                .setKey(Key.getDefaultInstance())
                .setStakedNodeId(0)
                .setEthereumNonce(1)
                .build();
        final var token =
                Token.newBuilder().addCustomFees(CustomFee.getDefaultInstance()).build();

        assertThat(StateChangeContext.isPresent(full, Account.getDefaultInstance(), Account.ALIAS_FIELD_NUMBER))
                .isTrue();
        // an empty message, a oneof member with the default value, and a non-default scalar are present
        assertThat(StateChangeContext.isPresent(partial, account, Account.KEY_FIELD_NUMBER))
                .isTrue();
        assertThat(StateChangeContext.isPresent(partial, account, Account.STAKED_NODE_ID_FIELD_NUMBER))
                .isTrue();
        assertThat(StateChangeContext.isPresent(partial, account, Account.ETHEREUM_NONCE_FIELD_NUMBER))
                .isTrue();
        assertThat(StateChangeContext.isPresent(partial, token, Token.CUSTOM_FEES_FIELD_NUMBER))
                .isTrue();
        // a cleared field is known to have the default value
        assertThat(StateChangeContext.isPresent(partial, account, Account.MEMO_FIELD_NUMBER))
                .isTrue();
        // absent fields are unchanged
        assertThat(StateChangeContext.isPresent(partial, account, Account.ALIAS_FIELD_NUMBER))
                .isFalse();
        assertThat(StateChangeContext.isPresent(partial, Token.getDefaultInstance(), Token.CUSTOM_FEES_FIELD_NUMBER))
                .isFalse();
        assertThat(StateChangeContext.isPresent(partial, account, 9999)).isFalse();
    }

    @Test
    void partialContractBytecode() {
        // given
        final var contractId = getContractId();
        final var stateChanges = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_BYTECODE_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setContractIdKey(contractId))
                                .setValue(MapChangeValue.newBuilder().setBytecodeValue(Bytecode.newBuilder()))
                                .setPartial(true)))
                .build();

        // when
        final var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.getContractBytecode(contractId)).isNull();
    }

    @Test
    void partialPendingFungibleAirdrop() {
        // given
        final var pendingAirdropId = getPendingAirdropId();
        final var stateChanges = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_PENDING_AIRDROPS_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setPendingAirdropIdKey(pendingAirdropId))
                                .setValue(MapChangeValue.newBuilder()
                                        .setAccountPendingAirdropValue(AccountPendingAirdrop.newBuilder()
                                                .setNextAirdrop(getPendingAirdropId())))
                                .setPartial(true)))
                .build();

        // when
        final var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.trackPendingFungibleAirdrop(pendingAirdropId, 100L)).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void partialStorage(final boolean hook) {
        // given
        final var stateChange = hook
                ? hookStorageMapUpdateChange(getEvmHookSlotKey(1L, bytes(32)), bytes(32))
                : contractStorageMapUpdateChange(getContractId(), bytes(32), bytes(32));
        final var builder = stateChange.toBuilder();
        builder.getMapUpdateBuilder().setPartial(true);
        final var stateChanges =
                StateChanges.newBuilder().addStateChanges(builder).build();

        // when, then
        assertThatThrownBy(() -> new StateChangeContext(List.of(stateChanges)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void partialToken() {
        // given
        final var supplyChanged = getTokenId();
        final var supplyCleared = getTokenId();
        final var supplyUnchanged = getTokenId();
        final var stateChanges = StateChanges.newBuilder()
                .addStateChanges(partialTokenStateChange(
                        supplyChanged, Token.newBuilder().setTotalSupply(3000L)))
                .addStateChanges(
                        partialTokenStateChange(supplyCleared, Token.newBuilder(), Token.TOTAL_SUPPLY_FIELD_NUMBER))
                .addStateChanges(partialTokenStateChange(
                        supplyUnchanged, Token.newBuilder().setMemo("memo")))
                .build();

        // when
        final var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.isTokenTotalSupplyUnknown(supplyChanged)).isFalse();
        assertThat(context.trackTokenTotalSupply(supplyChanged, 100L)).isEqualTo(3000L);
        assertThat(context.isTokenTotalSupplyUnknown(supplyCleared)).isFalse();
        assertThat(context.trackTokenTotalSupply(supplyCleared, 100L)).isEqualTo(0L);
        assertThat(context.isTokenTotalSupplyUnknown(supplyUnchanged)).isTrue();
        assertThat(context.trackTokenTotalSupply(supplyUnchanged, 100L)).isNull();
        assertThat(Arrays.asList(context.getNewTokenId(), context.getNewTokenId(), context.getNewTokenId()))
                .doesNotContainNull();
    }

    @Test
    void partialTopic() {
        // given
        final var topicId = getTopicId();
        final var stateChanges = StateChanges.newBuilder()
                .addStateChanges(StateChange.newBuilder()
                        .setStateId(STATE_ID_TOPICS_VALUE)
                        .setMapUpdate(MapUpdateChange.newBuilder()
                                .setKey(MapChangeKey.newBuilder().setTopicIdKey(topicId))
                                .setValue(MapChangeValue.newBuilder()
                                        .setTopicValue(Topic.newBuilder().setSequenceNumber(2)))
                                .setPartial(true)))
                .build();

        // when
        final var context = new StateChangeContext(List.of(stateChanges));

        // then
        assertThat(context.getNewTopicId()).isEqualTo(topicId);
        assertThat(context.getTopicMessage(topicId)).isNull();
    }

    private ByteString alias() {
        return DomainUtils.fromBytes(CommonUtils.nextBytes(34));
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

    // The account value omits the account id, as in a partial update, so it must be read from the key
    private StateChange accountMapUpdateChange(AccountID accountId, ByteString alias) {
        return accountMapUpdateChange(accountId, Account.newBuilder().setAlias(alias));
    }

    private StateChange accountMapUpdateChange(final AccountID accountId, final Account.Builder account) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_ACCOUNTS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                        .setValue(MapChangeValue.newBuilder().setAccountValue(account))
                        .build())
                .build();
    }

    private StateChange partialAccountMapUpdateChange(
            final AccountID accountId, final Account.Builder account, final Integer... clearedFields) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_ACCOUNTS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setAccountIdKey(accountId))
                        .setValue(MapChangeValue.newBuilder().setAccountValue(account))
                        .setPartial(true)
                        .addAllClearedFields(List.of(clearedFields)))
                .build();
    }

    private StateChange partialTokenStateChange(
            final TokenID tokenId, final Token.Builder token, final Integer... clearedFields) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_TOKENS_VALUE)
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                        .setValue(MapChangeValue.newBuilder().setTokenValue(token))
                        .setPartial(true)
                        .addAllClearedFields(List.of(clearedFields)))
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

    private ContractID toContractId(final AccountID accountId) {
        return ContractID.newBuilder()
                .setShardNum(accountId.getShardNum())
                .setRealmNum(accountId.getRealmNum())
                .setContractNum(accountId.getAccountNum())
                .build();
    }

    @Test
    void getHookStorageValueWritten() {
        // given
        var hookSlot1 = bytes(24);
        var hookSlot1Padded = DomainUtils.fromBytes(Bytes.concat(new byte[8], DomainUtils.toBytes(hookSlot1)));
        var hookSlot1Value = bytes(8);
        var hookSlot2 = bytes(32);
        var hookSlot2Value = bytes(12);
        var hookSlotKey1 = getEvmHookSlotKey(1L, hookSlot1Padded);
        var hookSlotKey2 = getEvmHookSlotKey(2L, hookSlot2);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(hookStorageMapUpdateChange(hookSlotKey1, hookSlot1Value))
                .addStateChanges(hookStorageMapUpdateChange(hookSlotKey2, hookSlot2Value))
                .addStateChanges(otherMapUpdateChange())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));

        // then
        // Test retrieval with normalized key
        var normalizedSlotKey1 =
                convert(hookSlotKey1.toBuilder().setKey(hookSlot1).build());
        assertThat(context.getContractStorageValueWritten(normalizedSlotKey1)).isEqualTo(BytesValue.of(hookSlot1Value));
        // Test retrieval with padded key (should normalize and find the value)
        assertThat(context.getContractStorageValueWritten(convert(hookSlotKey2)))
                .isEqualTo(BytesValue.of(hookSlot2Value));
        // Test non-existent key
        var nonExistentKey = getEvmHookSlotKey(3L, bytes(32));
        assertThat(context.getContractStorageValueWritten(convert(nonExistentKey)))
                .isNull();
    }

    @Test
    void getHookStorageValueWrittenWithEmptyContext() {
        // given
        var hookSlotKey = getEvmHookSlotKey(1L, bytes(32));

        // when, then
        assertThat(EMPTY_CONTEXT.getContractStorageValueWritten(convert(hookSlotKey)))
                .isNull();
    }

    @Test
    void getHookStorageValueWrittenWithMapDelete() {
        // given
        var hookSlot1 = bytes(32);
        var hookSlot1Value = bytes(8);
        var hookSlot2 = bytes(32);
        var hookSlot2Value = bytes(12);
        var hookSlot3 = bytes(32);
        var hookSlotKey1 = getEvmHookSlotKey(1L, hookSlot1);
        var hookSlotKey2 = getEvmHookSlotKey(2L, hookSlot2);
        var hookSlotKey3 = getEvmHookSlotKey(3L, hookSlot3);
        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(hookStorageMapUpdateChange(hookSlotKey1, hookSlot1Value))
                .addStateChanges(hookStorageMapUpdateChange(hookSlotKey2, hookSlot2Value))
                .addStateChanges(hookStorageMapDeleteChange(hookSlotKey2))
                .addStateChanges(otherMapUpdateChange())
                .build();

        // when
        var context = new StateChangeContext(List.of(stateChanges));

        // then
        // Test that updated value is present
        assertThat(context.getContractStorageValueWritten(convert(hookSlotKey1)))
                .isEqualTo(BytesValue.of(hookSlot1Value));
        // Test that deleted slot has default BytesValue (empty)
        assertThat(context.getContractStorageValueWritten(convert(hookSlotKey2)))
                .isEqualTo(BytesValue.getDefaultInstance());
        // Test non-existent key
        assertThat(context.getContractStorageValueWritten(convert(hookSlotKey3)))
                .isNull();
    }
}
