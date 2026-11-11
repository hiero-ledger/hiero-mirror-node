// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.hiero.mirror.common.util.DomainUtils.normalize;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Message;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.SingletonUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.hapi.node.state.hooks.legacy.EvmHookSlotKey;
import com.hederahashgraph.api.proto.java.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AccountPendingAirdrop;
import com.hederahashgraph.api.proto.java.Bytecode;
import com.hederahashgraph.api.proto.java.ClprChannel;
import com.hederahashgraph.api.proto.java.ClprConnector;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import com.hederahashgraph.api.proto.java.ClprEndpointManifest;
import com.hederahashgraph.api.proto.java.ClprLedgerConfiguration;
import com.hederahashgraph.api.proto.java.ClprMessageKey;
import com.hederahashgraph.api.proto.java.ClprMessageValue;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.SlotKey;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Topic;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    // A null value means the value written is absent from a partial state change
    private final Map<ContractSlotKey, @Nullable BytesValue> contractStorageChanges = new HashMap<>();
    private final Map<ContractSlotId, List<SlotValue>> contractStorageChangesIndexed = new HashMap<>();
    private final Map<ByteString, ClprChannel> clprChannels = new HashMap<>();
    private final Map<ClprConnectorKey, ClprConnector> clprConnectors = new HashMap<>();
    private final List<ClprConnectorKey> clprConnectorsRemoved = new ArrayList<>();

    private @Nullable ClprEndpointManifest clprEndpointManifest;
    private @Nullable ClprLedgerConfiguration clprLedgerConfiguration;

    private final List<ClprMessageEntry> clprMessages = new ArrayList<>();
    private final List<Long> nodeIds = new ArrayList<>();
    private final List<FileID> fileIds = new ArrayList<>();
    private final Map<PendingAirdropId, Long> pendingFungibleAirdrops = new HashMap<>();
    private final List<Long> registeredNodeIds = new ArrayList<>();
    private final List<TokenID> tokenIds = new ArrayList<>();
    private final Map<TokenID, Long> tokenTotalSupplies = new HashMap<>();
    private final Set<TokenID> tokensWithAbsentTotalSupply = new HashSet<>();
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
                        case StateIdentifier.STATE_ID_CLPR_CHANNELS_VALUE -> processClprChannelStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_CLPR_CONNECTORS_VALUE ->
                            processClprConnectorStateChange(mapUpdate);
                        case StateIdentifier.STATE_ID_CLPR_MESSAGE_QUEUE_VALUE ->
                            processClprMessageStateChange(mapUpdate);
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
                        // No case for channels: closure is always a status transition
                        // (CLOSING/DRAINED/CLOSED), never a removal from the clpr_channels map
                        case CLPR_CONNECTOR_KEY -> clprConnectorsRemoved.add(key.getClprConnectorKey());
                        case EVM_HOOK_SLOT_KEY -> processContractStorageDelete(key.getEvmHookSlotKey());
                        case SLOT_KEY_KEY -> processContractStorageDelete(key.getSlotKeyKey());
                        default -> {
                            // do nothing
                        }
                    }
                } else if (stateChange.hasSingletonUpdate()) {
                    final var singletonUpdate = stateChange.getSingletonUpdate();
                    switch (stateChange.getStateId()) {
                        // Carried by the system-initiated ClprMaintenance transaction -- see
                        // ClprMaintenanceTransformer/ClprMaintenanceTransactionHandler.
                        case StateIdentifier.STATE_ID_CLPR_ENDPOINT_MANIFEST_VALUE ->
                            processClprEndpointManifestStateChange(singletonUpdate);
                        case StateIdentifier.STATE_ID_CLPR_LEDGER_CONFIGURATION_VALUE ->
                            processClprLedgerConfigurationStateChange(singletonUpdate);
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

    public @Nullable ClprChannel getClprChannel(ByteString channelId) {
        return clprChannels.get(channelId);
    }

    public List<ClprChannelEntry> getClprChannels() {
        final var result = new ArrayList<ClprChannelEntry>(clprChannels.size());
        for (final var entry : clprChannels.entrySet()) {
            result.add(new ClprChannelEntry(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public @Nullable ClprConnector getClprConnector(ClprConnectorKey key) {
        return clprConnectors.get(key);
    }

    public List<ClprConnectorEntry> getClprConnectors() {
        final var result = new ArrayList<ClprConnectorEntry>(clprConnectors.size());
        for (final var entry : clprConnectors.entrySet()) {
            result.add(new ClprConnectorEntry(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public List<ClprConnectorKey> getClprConnectorsRemoved() {
        return Collections.unmodifiableList(clprConnectorsRemoved);
    }

    public Optional<ClprEndpointManifest> getClprEndpointManifest() {
        return Optional.ofNullable(clprEndpointManifest);
    }

    public Optional<ClprLedgerConfiguration> getClprLedgerConfiguration() {
        return Optional.ofNullable(clprLedgerConfiguration);
    }

    public List<ClprMessageEntry> getClprMessages() {
        return Collections.unmodifiableList(clprMessages);
    }


    public @Nullable ByteString getContractBytecode(final ContractID id) {
        return contractBytecodes.get(id);
    }

    /**
     * Get the id of the contract with the EVM address, or null if absent or the contract is created in this transactional
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
     * Whether the value written to the storage slot is absent, i.e., it's omitted from a partial state change because
     * the slot has no net change in the transactional unit.
     */
    public boolean isContractStorageValueWrittenAbsent(final ContractSlotKey slotKey) {
        final var normalized = normalize(slotKey);
        return contractStorageChanges.get(normalized) == null && contractStorageChanges.containsKey(normalized);
    }

    /**
     * Whether the token's total supply is absent, i.e., it's omitted from a partial state change because the net change
     * of the transactional unit is zero.
     */
    public boolean isTokenTotalSupplyAbsent(final TokenID tokenId) {
        return tokensWithAbsentTotalSupply.contains(tokenId);
    }

    /** Cheap presence check against the backing maps/list directly, without the allocations the getters make. */
    public boolean hasClprState() {
        return !clprChannels.isEmpty()
                || !clprMessages.isEmpty()
                || !clprConnectors.isEmpty()
                || !clprConnectorsRemoved.isEmpty();
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

    /**
     * Get the value written to a storage slot, or null if it's omitted from a partial state change, i.e., the slot is
     * written without a net change in the transactional unit.
     */
    private static @Nullable BytesValue getValueWritten(final MapUpdateChange mapUpdate) {
        final var slotValue = mapUpdate.getValue().getSlotValueValue();
        return isPresent(mapUpdate, slotValue, com.hederahashgraph.api.proto.java.SlotValue.VALUE_FIELD_NUMBER)
                ? BytesValue.of(slotValue.getValue())
                : null;
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

    private void processClprChannelStateChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getValue().hasClprChannelValue()) {
            return;
        }

        final var channelId = mapUpdate.getKey().getProtoBytesKey().getValue();
        clprChannels.put(channelId, mapUpdate.getValue().getClprChannelValue());
    }

    private void processClprConnectorStateChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasClprConnectorKey() || !mapUpdate.getValue().hasClprConnectorValue()) {
            return;
        }

        clprConnectors.put(
                mapUpdate.getKey().getClprConnectorKey(), mapUpdate.getValue().getClprConnectorValue());
    }

    private void processClprEndpointManifestStateChange(SingletonUpdateChange singletonUpdate) {
        if (!singletonUpdate.hasClprEndpointManifestValue()) {
            return;
        }

        clprEndpointManifest = singletonUpdate.getClprEndpointManifestValue();
    }

    private void processClprLedgerConfigurationStateChange(SingletonUpdateChange singletonUpdate) {
        if (!singletonUpdate.hasClprLedgerConfigurationValue()) {
            return;
        }

        clprLedgerConfiguration = singletonUpdate.getClprLedgerConfigurationValue();
    }

    private void processClprMessageStateChange(MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasClprMessageKey() || !mapUpdate.getValue().hasClprMessageValue()) {
            return;
        }

        clprMessages.add(new ClprMessageEntry(
                mapUpdate.getKey().getClprMessageKey(), mapUpdate.getValue().getClprMessageValue()));
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

        var slotKey = mapUpdate.getKey().getSlotKeyKey();
        final var slotId = ContractSlotId.of(slotKey.getContractID(), null);
        final var contractSlotKey = new ContractSlotKey(slotId, slotKey.getKey());
        processContractStorageChange(contractSlotKey, getValueWritten(mapUpdate));
    }

    private void processHookStorageChange(final MapUpdateChange mapUpdate) {
        if (!mapUpdate.getKey().hasEvmHookSlotKey() || mapUpdate.getIdentical()) {
            return;
        }

        final var evmHookSlotKey = mapUpdate.getKey().getEvmHookSlotKey();
        final var slotId = ContractSlotId.of(null, evmHookSlotKey.getHookId());
        final var contractSlotKey = new ContractSlotKey(slotId, evmHookSlotKey.getKey());
        processContractStorageChange(contractSlotKey, getValueWritten(mapUpdate));
    }

    private void processContractStorageChange(ContractSlotKey slotKey, @Nullable BytesValue valueWritten) {
        slotKey = normalize(slotKey);
        final var trimmed = valueWritten != null ? DomainUtils.trim(valueWritten) : null;
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
            tokensWithAbsentTotalSupply.add(tokenId);
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
     * A storage slot and the value written to it, or null if the value is absent.
     */
    public record SlotValue(ByteString slot, @Nullable BytesValue valueWritten) {}

    public record ClprChannelEntry(ByteString channelId, ClprChannel value) {}

    public record ClprConnectorEntry(ClprConnectorKey key, ClprConnector value) {}

    public record ClprMessageEntry(ClprMessageKey key, ClprMessageValue value) {}
}
