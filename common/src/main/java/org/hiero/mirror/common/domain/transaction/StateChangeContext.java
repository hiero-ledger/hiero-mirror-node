// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.hiero.mirror.common.util.DomainUtils.normalize;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.SlotKey;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.NonNull;

public final class StateChangeContext {

    static final StateChangeContext EMPTY_CONTEXT = new StateChangeContext();

    private static final Comparator<FileID> FILE_ID_COMPARATOR = Comparator.comparing(FileID::getFileNum);
    private static final Comparator<Long> NODE_ID_COMPARATOR = Comparator.naturalOrder();
    private static final Comparator<TokenID> TOKEN_ID_COMPARATOR = Comparator.comparing(TokenID::getTokenNum);
    private static final Comparator<TopicID> TOPIC_ID_COMPARATOR = Comparator.comparing(TopicID::getTopicNum);

    private final Map<AccountID, Account> accounts = new HashMap<>();
    private final Map<ContractID, ByteString> contractBytecodes = new HashMap<>();
    private final Map<ByteString, ContractID> contractIds = new HashMap<>();
    private final Map<SlotKey, BytesValue> contractStorageChanges = new HashMap<>();
    private final Map<ContractID, List<SlotValue>> contractStorageChangesIndexed = new HashMap<>();
    private final List<Long> nodeIds = new LinkedList<>();
    private final List<FileID> fileIds = new LinkedList<>();
    private final Map<PendingAirdropId, Long> pendingFungibleAirdrops = new HashMap<>();
    private final List<TokenID> tokenIds = new LinkedList<>();
    private final Map<TokenID, Long> tokenTotalSupplies = new HashMap<>();
    private final List<TopicID> topicIds = new LinkedList<>();
    private final Map<TopicID, TopicMessage> topicState = new HashMap<>();

    private StateChangeContext() {}

    /**
     * Create a context from the state changes. Note the contract is for an entity id, there should be at most
     * one state change of a certain key type and value type combination. This guarantees the entity ids in a list
     * are unique.
     *
     * @param stateChangesList - A list of state changes
     */
    StateChangeContext(List<StateChanges> stateChangesList) {
        for (var stateChanges : stateChangesList) {
            for (var stateChange : stateChanges.getStateChangesList()) {
                if (stateChange.hasMapUpdate()) {
                    var mapUpdate = stateChange.getMapUpdate();
                    switch (stateChange.getStateId()) {
                        case StateIdentifier.STATE_ID_ACCOUNTS_VALUE -> processAccountStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_CONTRACT_BYTECODE_VALUE -> processContractBytecode(mapUpdate);
                        case StateIdentifier.STATE_ID_CONTRACT_STORAGE_VALUE -> processContractStorageChange(mapUpdate);
                        case StateIdentifier.STATE_ID_FILES_VALUE ->
                            fileIds.add(mapUpdate.getKey().getFileIdKey());
                        case StateIdentifier.STATE_ID_NODES_VALUE -> processNodeStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE ->
                            processPendingAirdropStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_TOKENS_VALUE -> processTokenStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_TOPICS_VALUE -> processTopicStateChange(mapUpdate);
                        default -> {
                            // do nothing
                        }
                    }
                } else if (stateChange.hasMapDelete()
                        && stateChange.getMapDelete().getKey().hasSlotKeyKey()) {
                    var slotKey = stateChange.getMapDelete().getKey().getSlotKeyKey();
                    // use the default BytesValue instance when the storage slot is deleted
                    processContractStorageChange(slotKey, BytesValue.getDefaultInstance());
                }
            }
        }

        fileIds.sort(FILE_ID_COMPARATOR);
        nodeIds.sort(NODE_ID_COMPARATOR);
        tokenIds.sort(TOKEN_ID_COMPARATOR);
        topicIds.sort(TOPIC_ID_COMPARATOR);
    }

    public Optional<Account> getAccount(@NonNull AccountID id) {
        return Optional.ofNullable(accounts.get(id));
    }

    public Optional<ByteString> getContractBytecode(@NonNull ContractID id) {
        return Optional.ofNullable(contractBytecodes.get(id));
    }

    public Optional<ContractID> getContractId(@NonNull ByteString evmAddress) {
        return Optional.ofNullable(contractIds.get(evmAddress));
    }

    public SlotValue getContractStorageChange(@NonNull ContractID contractId, int index) {
        if (index < 0) {
            return null;
        }

        var indexed = contractStorageChangesIndexed.get(contractId);
        if (indexed == null || index >= indexed.size()) {
            return null;
        }

        return indexed.get(index);
    }

    public BytesValue getContractStorageValueWritten(@NonNull SlotKey slotKey) {
        return contractStorageChanges.get(normalize(slotKey));
    }

    public Optional<FileID> getNewFileId() {
        if (fileIds.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(fileIds.removeLast());
    }

    public Optional<Long> getNewNodeId() {
        if (nodeIds.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(nodeIds.removeLast());
    }

    public Optional<TokenID> getNewTokenId() {
        if (tokenIds.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(tokenIds.removeLast());
    }

    public Optional<TopicID> getNewTopicId() {
        if (topicIds.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(topicIds.removeLast());
    }

    public Optional<TopicMessage> getTopicMessage(@NonNull TopicID topicId) {
        return Optional.ofNullable(topicState.remove(topicId));
    }

    /**
     * Get the current amount of a pending fungible airdrop and track its renaming amount.
     *
     * @param pendingAirdropId - The pending fungible airdrop id
     * @param change - The amount of change to track
     * @return An optional of the pending airdrop's amount
     */
    public Optional<Long> trackPendingFungibleAirdrop(@NonNull PendingAirdropId pendingAirdropId, long change) {
        return Optional.ofNullable(pendingFungibleAirdrops.remove(pendingAirdropId))
                .map(amount -> {
                    if (change < amount) {
                        pendingFungibleAirdrops.put(pendingAirdropId, amount - change);
                    }

                    return amount;
                });
    }

    /**
     * Get the current token total supply and track its change.
     *
     * @param tokenId - The token id
     * @param change - The amount of change to track. Note for transactions which increased the total supply, the value
     *               should be negative; for transactions which reduced the total supply, the value should be positive
     * @return An optional of the token total supply
     */
    public Optional<Long> trackTokenTotalSupply(@NonNull TokenID tokenId, long change) {
        return Optional.ofNullable(tokenTotalSupplies.get(tokenId)).map(totalSupply -> {
            tokenTotalSupplies.put(tokenId, totalSupply + change);
            return totalSupply;
        });
    }

    private void processAccountStateChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getValue().hasAccountValue()) {
            return;
        }

        var account = mapUpdate.getValue().getAccountValue();
        accounts.putIfAbsent(account.getAccountId(), account);

        if (account.getSmartContract() && account.getAlias() != ByteString.EMPTY) {
            var accountId = account.getAccountId();
            contractIds.put(
                    account.getAlias(),
                    ContractID.newBuilder()
                            .setShardNum(accountId.getShardNum())
                            .setRealmNum(accountId.getRealmNum())
                            .setContractNum(accountId.getAccountNum())
                            .build());
        }
    }

    private void processContractBytecode(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getValue().hasBytecodeValue()) {
            return;
        }

        var contractId = mapUpdate.getKey().getContractIdKey();
        var bytecode = mapUpdate.getValue().getBytecodeValue();
        contractBytecodes.put(contractId, bytecode.getCode());
    }

    private void processContractStorageChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasSlotKeyKey() || mapUpdate.getIdentical()) {
            return;
        }

        var slotKey = mapUpdate.getKey().getSlotKeyKey();
        var valueWritten = mapUpdate.getValue().getSlotValueValue().getValue();
        processContractStorageChange(slotKey, BytesValue.of(valueWritten));
    }

    private void processContractStorageChange(SlotKey slotKey, BytesValue valueWritten) {
        slotKey = normalize(slotKey);
        contractStorageChanges.put(slotKey, valueWritten);
        contractStorageChangesIndexed
                .computeIfAbsent(slotKey.getContractID(), c -> new ArrayList<>())
                .add(new SlotValue(slotKey.getKey(), valueWritten));
    }

    private void processNodeStateChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasEntityNumberKey()) {
            return;
        }

        nodeIds.add(mapUpdate.getKey().getEntityNumberKey().getValue());
    }

    private void processPendingAirdropStateChange(MapUpdateChange mapUpdate) {
        var pendingAirdropId = mapUpdate.getKey().getPendingAirdropIdKey();
        if (pendingAirdropId.hasFungibleTokenType()) {
            pendingFungibleAirdrops.put(
                    pendingAirdropId,
                    mapUpdate
                            .getValue()
                            .getAccountPendingAirdropValue()
                            .getPendingAirdropValue()
                            .getAmount());
        }
    }

    private void processTokenStateChange(MapUpdateChange mapUpdate) {
        var token = mapUpdate.getValue().getTokenValue();
        tokenIds.add(token.getTokenId());
        tokenTotalSupplies.put(token.getTokenId(), token.getTotalSupply());
    }

    private void processTopicStateChange(MapUpdateChange mapUpdate) {
        var topic = mapUpdate.getValue().getTopicValue();
        topicIds.add(topic.getTopicId());
        if (topic.getSequenceNumber() != 0) {
            topicState.put(
                    topic.getTopicId(),
                    TopicMessage.builder()
                            .runningHash(DomainUtils.toBytes(topic.getRunningHash()))
                            .sequenceNumber(topic.getSequenceNumber())
                            .build());
        }
    }

    public record SlotValue(ByteString slot, BytesValue valueWritten) {}
}
