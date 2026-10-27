// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.hiero.mirror.common.util.DomainUtils.normalize;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Message;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.hapi.node.state.hooks.legacy.EvmHookSlotKey;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AccountPendingAirdrop;
import com.hederahashgraph.api.proto.java.Bytecode;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.SlotKey;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Topic;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final Map<AccountID, ByteString> accountAliases = new HashMap<>();
    private final Map<AccountID, Long> accountEthereumNonces = new HashMap<>();
    private final Map<ContractID, ByteString> contractBytecodes = new HashMap<>();
    private final Map<ByteString, ContractID> contractIds = new HashMap<>();
    private final Map<ContractSlotKey, BytesValue> contractStorageChanges = new HashMap<>();
    private final Map<ContractSlotId, List<SlotValue>> contractStorageChangesIndexed = new HashMap<>();
    private final List<Long> nodeIds = new ArrayList<>();
    private final List<FileID> fileIds = new ArrayList<>();
    private final Map<PendingAirdropId, Long> pendingFungibleAirdrops = new HashMap<>();
    private final List<Long> registeredNodeIds = new ArrayList<>();
    private final List<TokenID> tokenIds = new ArrayList<>();
    private final Map<TokenID, Long> tokenTotalSupplies = new HashMap<>();
    private final Set<TokenID> tokensWithUnknownTotalSupply = new HashSet<>();
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
                        case StateIdentifier.STATE_ID_EVM_HOOK_STORAGE_VALUE -> processHookStorageChange(mapUpdate);
                        case StateIdentifier.STATE_ID_FILES_VALUE ->
                            fileIds.add(mapUpdate.getKey().getFileIdKey());
                        case StateIdentifier.STATE_ID_NODES_VALUE -> processNodeStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE ->
                            processPendingAirdropStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_REGISTERED_NODES_VALUE -> processRegisteredNodeChange(mapUpdate);
                        case StateIdentifier.STATE_ID_STORAGE_VALUE -> processContractStorageChange(mapUpdate);
                        case StateIdentifier.STATE_ID_TOKENS_VALUE -> processTokenStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_TOPICS_VALUE -> processTopicStateChange(mapUpdate);
                        default -> {
                            // do nothing
                        }
                    }
                } else if (stateChange.hasMapDelete()) {
                    final var key = stateChange.getMapDelete().getKey();
                    switch (key.getKeyChoiceCase()) {
                        case EVM_HOOK_SLOT_KEY -> processContractStorageDelete(key.getEvmHookSlotKey());
                        case SLOT_KEY_KEY -> processContractStorageDelete(key.getSlotKeyKey());
                        default -> {
                            // do nothing
                        }
                    }
                }
            }
        }

        fileIds.sort(FILE_ID_COMPARATOR);
        nodeIds.sort(NODE_ID_COMPARATOR);
        tokenIds.sort(TOKEN_ID_COMPARATOR);
        topicIds.sort(TOPIC_ID_COMPARATOR);
    }

    public @Nullable ByteString getAccountAlias(final AccountID id) {
        return accountAliases.get(id);
    }

    public @Nullable Long getAccountEthereumNonce(final AccountID id) {
        return accountEthereumNonces.get(id);
    }

    public @Nullable ByteString getContractBytecode(final ContractID id) {
        return contractBytecodes.get(id);
    }

    /**
     * Get the id of the contract with the EVM address, or null if unknown or the contract is created in this transactional
     * unit, since the state changes only have the final state and the address may be reused after a delete.
     */
    public @Nullable ContractID getContractId(final ByteString evmAddress) {
        final var contractId = contractIds.get(evmAddress);
        return contractId == null || contractBytecodes.containsKey(contractId) ? null : contractId;
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

    public @Nullable FileID getNewFileId() {
        return fileIds.isEmpty() ? null : fileIds.removeLast();
    }

    public @Nullable Long getNewNodeId() {
        return nodeIds.isEmpty() ? null : nodeIds.removeLast();
    }

    public @Nullable Long getNewRegisteredNodeId() {
        return registeredNodeIds.isEmpty() ? null : registeredNodeIds.removeLast();
    }

    public @Nullable TokenID getNewTokenId() {
        return tokenIds.isEmpty() ? null : tokenIds.removeLast();
    }

    public @Nullable TopicID getNewTopicId() {
        return topicIds.isEmpty() ? null : topicIds.removeLast();
    }

    public @Nullable TopicMessage getTopicMessage(final TopicID topicId) {
        return topicState.remove(topicId);
    }

    /**
     * Whether the token's total supply is unknown, i.e., it's omitted from a partial state change because the net change
     * of the transactional unit is zero.
     */
    public boolean isTokenTotalSupplyUnknown(final TokenID tokenId) {
        return tokensWithUnknownTotalSupply.contains(tokenId);
    }

    /**
     * Get the current amount of a pending fungible airdrop and track its renaming amount.
     *
     * @param pendingAirdropId - The pending fungible airdrop id
     * @param change           - The amount of change to track
     * @return The pending airdrop's amount, or null if it's not in state
     */
    public @Nullable Long trackPendingFungibleAirdrop(final PendingAirdropId pendingAirdropId, final long change) {
        final var amount = pendingFungibleAirdrops.remove(pendingAirdropId);
        if (amount != null && change < amount) {
            pendingFungibleAirdrops.put(pendingAirdropId, amount - change);
        }

        return amount;
    }

    /**
     * Get the current token total supply and track its change.
     *
     * @param tokenId - The token id
     * @param change  - The amount of change to track. Note for transactions which increased the total supply, the value
     *                should be negative; for transactions which reduced the total supply, the value should be positive
     * @return The token total supply, or null if it's not in state
     */
    public @Nullable Long trackTokenTotalSupply(final TokenID tokenId, final long change) {
        final var totalSupply = tokenTotalSupplies.get(tokenId);
        if (totalSupply != null) {
            tokenTotalSupplies.put(tokenId, totalSupply + change);
        }

        return totalSupply;
    }

    /**
     * Whether a field of a state change value is present, i.e., the update is full, or the field is set or cleared in a
     * partial update. Any other field in a partial update is unchanged, so its value is absent.
     */
    static boolean isPresent(final MapUpdateChange mapUpdate, final Message value, final int fieldNumber) {
        if (!mapUpdate.getPartial() || mapUpdate.getClearedFieldsList().contains(fieldNumber)) {
            return true;
        }

        final var field = value.getDescriptorForType().findFieldByNumber(fieldNumber);
        if (field == null) {
            return false;
        }

        return field.isRepeated() ? value.getRepeatedFieldCount(field) > 0 : value.hasField(field);
    }

    private void processAccountStateChange(final MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasAccountIdKey() || !mapUpdate.getValue().hasAccountValue()) {
            return;
        }

        // Read the entity id from the key, since the value of a partial update only has the changed fields
        final var accountId = mapUpdate.getKey().getAccountIdKey();
        final var account = mapUpdate.getValue().getAccountValue();
        if (isPresent(mapUpdate, account, Account.ETHEREUM_NONCE_FIELD_NUMBER)) {
            accountEthereumNonces.putIfAbsent(accountId, account.getEthereumNonce());
        }

        // A non-empty alias is always present, even in a partial update
        final var alias = account.getAlias();
        if (alias.isEmpty()) {
            return;
        }

        accountAliases.putIfAbsent(accountId, alias);
        if (account.getSmartContract()) {
            contractIds.put(
                    alias,
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
        if (isPresent(mapUpdate, bytecode, Bytecode.CODE_FIELD_NUMBER)) {
            contractBytecodes.put(contractId, bytecode.getCode());
        }
    }

    private void processContractStorageChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasSlotKeyKey() || mapUpdate.getIdentical()) {
            return;
        }

        requireFullValue(mapUpdate);
        var slotKey = mapUpdate.getKey().getSlotKeyKey();
        final var slotId = ContractSlotId.of(slotKey.getContractID(), null);
        final var contractSlotKey = new ContractSlotKey(slotId, slotKey.getKey());
        var valueWritten = mapUpdate.getValue().getSlotValueValue().getValue();
        processContractStorageChange(contractSlotKey, BytesValue.of(valueWritten));
    }

    private void processHookStorageChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasEvmHookSlotKey()) {
            return;
        }

        requireFullValue(mapUpdate);
        var valueWritten = mapUpdate.getValue().getSlotValueValue().getValue();
        final var evmHookSlotKey = mapUpdate.getKey().getEvmHookSlotKey();
        final var slotId = ContractSlotId.of(null, evmHookSlotKey.getHookId());
        final var contractSlotKey = new ContractSlotKey(slotId, evmHookSlotKey.getKey());
        processContractStorageChange(contractSlotKey, BytesValue.of(valueWritten));
    }

    private void processContractStorageChange(ContractSlotKey slotKey, BytesValue valueWritten) {
        slotKey = normalize(slotKey);
        final var trimmed = DomainUtils.trim(valueWritten);
        contractStorageChanges.put(slotKey, trimmed);

        contractStorageChangesIndexed
                .computeIfAbsent(slotKey.slotId(), _ -> new ArrayList<>())
                .add(new SlotValue(slotKey.key(), trimmed));
    }

    private void processContractStorageDelete(final ContractSlotKey contractSlotKey) {
        // use the default BytesValue instance when the storage slot is deleted
        processContractStorageChange(contractSlotKey, BytesValue.getDefaultInstance());
    }

    private void processContractStorageDelete(final EvmHookSlotKey evmHookStorageSlot) {
        final var slotId = ContractSlotId.of(null, evmHookStorageSlot.getHookId());
        processContractStorageDelete(new ContractSlotKey(slotId, evmHookStorageSlot.getKey()));
    }

    private void processContractStorageDelete(final SlotKey slotKey) {
        final var slotId = ContractSlotId.of(slotKey.getContractID(), null);
        processContractStorageDelete(new ContractSlotKey(slotId, slotKey.getKey()));
    }

    private void processNodeStateChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasEntityNumberKey()) {
            return;
        }

        nodeIds.add(mapUpdate.getKey().getEntityNumberKey().getValue());
    }

    private void processPendingAirdropStateChange(MapUpdateChange mapUpdate) {
        final var pendingAirdropId = mapUpdate.getKey().getPendingAirdropIdKey();
        final var pendingAirdrop = mapUpdate.getValue().getAccountPendingAirdropValue();
        if (pendingAirdropId.hasFungibleTokenType()
                && isPresent(mapUpdate, pendingAirdrop, AccountPendingAirdrop.PENDING_AIRDROP_VALUE_FIELD_NUMBER)) {
            pendingFungibleAirdrops.put(
                    pendingAirdropId, pendingAirdrop.getPendingAirdropValue().getAmount());
        }
    }

    private void processRegisteredNodeChange(final MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasEntityNumberKey()) {
            return;
        }

        registeredNodeIds.add(mapUpdate.getKey().getEntityNumberKey().getValue());
    }

    private void processTokenStateChange(final MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasTokenIdKey()) {
            return;
        }

        final var tokenId = mapUpdate.getKey().getTokenIdKey();
        tokenIds.add(tokenId);
        final var token = mapUpdate.getValue().getTokenValue();
        if (isPresent(mapUpdate, token, Token.TOTAL_SUPPLY_FIELD_NUMBER)) {
            tokenTotalSupplies.put(tokenId, token.getTotalSupply());
        } else {
            tokensWithUnknownTotalSupply.add(tokenId);
        }
    }

    private void processTopicStateChange(final MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasTopicIdKey()) {
            return;
        }

        final var topicId = mapUpdate.getKey().getTopicIdKey();
        topicIds.add(topicId);
        final var topic = mapUpdate.getValue().getTopicValue();
        if (topic.getSequenceNumber() != 0
                && isPresent(mapUpdate, topic, Topic.RUNNING_HASH_FIELD_NUMBER)
                && isPresent(mapUpdate, topic, Topic.SEQUENCE_NUMBER_FIELD_NUMBER)) {
            topicState.put(
                    topicId,
                    TopicMessage.builder()
                            .runningHash(DomainUtils.toBytes(topic.getRunningHash()))
                            .sequenceNumber(topic.getSequenceNumber())
                            .build());
        }
    }

    /**
     * Contract and hook storage updates are always full by design, so a partial one can't be read as the value written.
     */
    private static void requireFullValue(final MapUpdateChange mapUpdate) {
        if (mapUpdate.getPartial()) {
            throw new IllegalStateException("Unexpected partial storage state change for key " + mapUpdate.getKey());
        }
    }

    public record SlotValue(ByteString slot, BytesValue valueWritten) {}
}
