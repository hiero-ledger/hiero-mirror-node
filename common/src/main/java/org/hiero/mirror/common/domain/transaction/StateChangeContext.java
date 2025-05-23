// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import jakarta.annotation.Nonnull;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;

public final class StateChangeContext {

    static final StateChangeContext EMPTY_CONTEXT = new StateChangeContext();

    private static final Comparator<FileID> FILE_ID_COMPARATOR = Comparator.comparing(FileID::getFileNum);
    private static final Comparator<Long> NODE_ID_COMPARATOR = Comparator.naturalOrder();
    private static final Comparator<TokenID> TOKEN_ID_COMPARATOR = Comparator.comparing(TokenID::getTokenNum);
    private static final Comparator<TopicID> TOPIC_ID_COMPARATOR = Comparator.comparing(TopicID::getTopicNum);

    private final Map<ByteString, ContractID> contractIds = new HashMap<>();
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
                if (!stateChange.hasMapUpdate()) {
                    continue;
                }

                var mapUpdate = stateChange.getMapUpdate();
                switch (stateChange.getStateId()) {
                    case StateIdentifier.STATE_ID_ACCOUNTS_VALUE -> processAccountStateChange(mapUpdate);
                    case StateIdentifier.STATE_ID_FILES_VALUE ->
                        fileIds.add(mapUpdate.getKey().getFileIdKey());
                    case StateIdentifier.STATE_ID_NODES_VALUE -> processNodeStateChange(mapUpdate);
                    case StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE -> processPendingAirdropStateChange(mapUpdate);
                    case StateIdentifier.STATE_ID_TOKENS_VALUE -> processTokenStateChange(mapUpdate);
                    case StateIdentifier.STATE_ID_TOPICS_VALUE -> processTopicStateChange(mapUpdate);
                    default -> {
                        // do nothing
                    }
                }
            }
        }

        fileIds.sort(FILE_ID_COMPARATOR);
        nodeIds.sort(NODE_ID_COMPARATOR);
        tokenIds.sort(TOKEN_ID_COMPARATOR);
        topicIds.sort(TOPIC_ID_COMPARATOR);
    }

    public Optional<ContractID> getContractId(@Nonnull ByteString evmAddress) {
        return Optional.ofNullable(contractIds.get(evmAddress));
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

    public Optional<TopicMessage> getTopicMessage(@Nonnull TopicID topicId) {
        return Optional.ofNullable(topicState.remove(topicId)).map(topicMessage -> {
            if (topicMessage.getSequenceNumber() > 1) {
                // running hash is lost for any earlier message, set it to an empty bytearray
                // since null is not allowed
                topicState.put(
                        topicId,
                        TopicMessage.builder()
                                .runningHash(DomainUtils.EMPTY_BYTE_ARRAY)
                                .sequenceNumber(topicMessage.getSequenceNumber() - 1)
                                .build());
            }
            return topicMessage;
        });
    }

    /**
     * Get the current amount of a pending fungible airdrop and track its renaming amount.
     *
     * @param pendingAirdropId - The pending fungible airdrop id
     * @param change - The amount of change to track
     * @return An optional of the pending airdrop's amount
     */
    public Optional<Long> trackPendingFungibleAirdrop(@Nonnull PendingAirdropId pendingAirdropId, long change) {
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
    public Optional<Long> trackTokenTotalSupply(@Nonnull TokenID tokenId, long change) {
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
}
