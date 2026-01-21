// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.hiero.mirror.common.util.DomainUtils.normalize;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.hapi.node.state.hooks.legacy.LambdaSlotKey;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class StateChangeContext {

    static final StateChangeContext EMPTY_CONTEXT = new StateChangeContext();

    private static final Comparator<FileID> FILE_ID_COMPARATOR = Comparator.comparing(FileID::getFileNum);
    private static final Comparator<Long> NODE_ID_COMPARATOR = Comparator.naturalOrder();
    private static final Comparator<TokenID> TOKEN_ID_COMPARATOR = Comparator.comparing(TokenID::getTokenNum);
    private static final Comparator<TopicID> TOPIC_ID_COMPARATOR = Comparator.comparing(TopicID::getTopicNum);

    private final Map<AccountID, Account> accounts = new HashMap<>();
    private final Map<ByteString, AccountID> accountIds = new HashMap<>();
    private final Map<ContractID, ByteString> contractBytecodes = new HashMap<>();
    private final Map<ByteString, ContractID> contractIds = new HashMap<>();
    private final Map<ContractSlotKey, BytesValue> contractStorageChanges = new HashMap<>();
    private final Map<ContractSlotId, List<SlotValue>> contractStorageChangesIndexed = new HashMap<>();
    private final List<Long> nodeIds = new ArrayList<>();
    private final List<FileID> fileIds = new ArrayList<>();
    private final Map<PendingAirdropId, Long> pendingFungibleAirdrops = new HashMap<>();
    private final List<TokenID> tokenIds = new ArrayList<>();
    private final Map<TokenID, Long> tokenTotalSupplies = new HashMap<>();
    private final List<TopicID> topicIds = new ArrayList<>();
    private final Map<TopicID, TopicMessage> topicState = new HashMap<>();

    private StateChangeContext() {}

    /**
     * Create a context from the state changes. Note the contract is for an entity id, there should be at most one state
     * change of a certain key type and value type combination. This guarantees the entity ids in a list are unique.
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
                        case StateIdentifier.STATE_ID_BYTECODE_VALUE -> processContractBytecode(mapUpdate);
                        case StateIdentifier.STATE_ID_STORAGE_VALUE -> processContractStorageChange(mapUpdate);
                        case StateIdentifier.STATE_ID_FILES_VALUE ->
                            fileIds.add(mapUpdate.getKey().getFileIdKey());
                        case StateIdentifier.STATE_ID_NODES_VALUE -> processNodeStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE ->
                            processPendingAirdropStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_TOKENS_VALUE -> processTokenStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_TOPICS_VALUE -> processTopicStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_LAMBDA_STORAGE_VALUE -> processLambdaStorageChange(mapUpdate);
                        default -> {
                            // do nothing
                        }
                    }
                } else if (stateChange.hasMapDelete()) {
                    MapChangeKey key = stateChange.getMapDelete().getKey();
                    if (key.hasSlotKeyKey()) {
                        SlotKey slotKey = key.getSlotKeyKey();
                        final var slotId = ContractSlotId.builder()
                                .contractId(slotKey.getContractID())
                                .build();
                        final var contractSlotKey = ContractSlotKey.builder()
                                .slotId(slotId)
                                .key(slotKey.getKey())
                                .build();
                        // use the default BytesValue instance when the storage slot is deleted
                        processContractStorageChange(contractSlotKey, BytesValue.getDefaultInstance());
                    }
                    if (key.hasLambdaSlotKey()) {
                        LambdaSlotKey lambdaSlotKey = key.getLambdaSlotKey();
                        final var slotId = ContractSlotId.builder()
                                .hookId(lambdaSlotKey.getHookId())
                                .build();
                        final var contractSlotKey = ContractSlotKey.builder()
                                .slotId(slotId)
                                .key(lambdaSlotKey.getKey())
                                .build();
                        // use the default BytesValue instance when the storage slot is deleted
                        processContractStorageChange(contractSlotKey, BytesValue.getDefaultInstance());
                    }
                }
            }
        }

        fileIds.sort(FILE_ID_COMPARATOR);
        nodeIds.sort(NODE_ID_COMPARATOR);
        tokenIds.sort(TOKEN_ID_COMPARATOR);
        topicIds.sort(TOPIC_ID_COMPARATOR);
    }

    public Optional<Account> getAccount(AccountID id) {
        return Optional.ofNullable(accounts.get(id));
    }

    public Optional<AccountID> getAccountId(ByteString alias) {
        return Optional.ofNullable(accountIds.get(alias));
    }

    public Optional<ByteString> getContractBytecode(ContractID id) {
        return Optional.ofNullable(contractBytecodes.get(id));
    }

    public Optional<ContractID> getContractId(ByteString evmAddress) {
        return Optional.ofNullable(contractIds.get(evmAddress));
    }

    public @Nullable SlotValue getContractStorageChange(ContractSlotId slotId, int index) {
        if (index < 0) {
            return null;
        }

        var indexed = contractStorageChangesIndexed.get(slotId);
        if (indexed == null || index >= indexed.size()) {
            return null;
        }

        return indexed.get(index);
    }

    public @Nullable BytesValue getContractStorageValueWritten(ContractSlotKey slotKey) {
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

    public Optional<TopicMessage> getTopicMessage(TopicID topicId) {
        return Optional.ofNullable(topicState.remove(topicId));
    }

    /**
     * Get the current amount of a pending fungible airdrop and track its renaming amount.
     *
     * @param pendingAirdropId - The pending fungible airdrop id
     * @param change           - The amount of change to track
     * @return An optional of the pending airdrop's amount
     */
    public Optional<Long> trackPendingFungibleAirdrop(PendingAirdropId pendingAirdropId, long change) {
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
     * @param change  - The amount of change to track. Note for transactions which increased the total supply, the value
     *                should be negative; for transactions which reduced the total supply, the value should be positive
     * @return An optional of the token total supply
     */
    public Optional<Long> trackTokenTotalSupply(TokenID tokenId, long change) {
        return Optional.ofNullable(tokenTotalSupplies.get(tokenId)).map(totalSupply -> {
            tokenTotalSupplies.put(tokenId, totalSupply + change);
            return totalSupply;
        });
    }

    private void processAccountStateChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getValue().hasAccountValue()) {
            return;
        }

        final var account = mapUpdate.getValue().getAccountValue();
        accounts.putIfAbsent(account.getAccountId(), account);

        if (!account.getAlias().equals(ByteString.EMPTY)) {
            final var accountId = account.getAccountId();
            final var alias = account.getAlias();

            if (account.getSmartContract()) {
                contractIds.put(
                        alias,
                        ContractID.newBuilder()
                                .setShardNum(accountId.getShardNum())
                                .setRealmNum(accountId.getRealmNum())
                                .setContractNum(accountId.getAccountNum())
                                .build());
            } else {
                accountIds.put(alias, accountId);
            }
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
        final var slotId = ContractSlotId.builder()
                .contractId(slotKey.getContractID())
                .hookId(null)
                .build();
        final var contractSlotKey =
                ContractSlotKey.builder().slotId(slotId).key(slotKey.getKey()).build();
        var valueWritten = mapUpdate.getValue().getSlotValueValue().getValue();
        processContractStorageChange(contractSlotKey, BytesValue.of(valueWritten));
    }

    private void processLambdaStorageChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasLambdaSlotKey()) {
            return;
        }
        var valueWritten = mapUpdate.getValue().getSlotValueValue().getValue();
        final var lambdaSlotKey = mapUpdate.getKey().getLambdaSlotKey();
        final var slotId = ContractSlotId.builder()
                .contractId(null)
                .hookId(lambdaSlotKey.getHookId())
                .build();
        final var contractSlotKey = ContractSlotKey.builder()
                .slotId(slotId)
                .key(lambdaSlotKey.getKey())
                .build();
        processContractStorageChange(contractSlotKey, BytesValue.of(valueWritten));
    }

    private void processContractStorageChange(ContractSlotKey slotKey, BytesValue valueWritten) {
        slotKey = normalize(slotKey);
        final var trimmed = DomainUtils.trim(valueWritten);
        contractStorageChanges.put(slotKey, trimmed);

        contractStorageChangesIndexed
                .computeIfAbsent(slotKey.slotId(), id -> new ArrayList<>())
                .add(new SlotValue(slotKey.key(), trimmed));
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
