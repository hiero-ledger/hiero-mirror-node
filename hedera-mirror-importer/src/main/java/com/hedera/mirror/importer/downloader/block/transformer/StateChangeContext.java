/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.downloader.block.transformer;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StateChangeContext {

    static final StateChangeContext EMPTY_CONTEXT = new StateChangeContext();

    private static final Comparator<FileID> FILE_ID_COMPARATOR =
            Comparator.comparing(FileID::getFileNum).reversed();
    private static final Comparator<Long> NODE_ID_COMPARATOR = Comparator.reverseOrder();
    private static final Comparator<TokenID> TOKEN_ID_COMPARATOR =
            Comparator.comparing(TokenID::getTokenNum).reversed();
    private static final Comparator<TopicID> TOPIC_ID_COMPARATOR =
            Comparator.comparing(TopicID::getTopicNum).reversed();

    private final Map<ByteString, ContractID> contractIds = new HashMap<>();
    private final List<Long> nodeIds = new ArrayList<>();
    private final List<FileID> fileIds = new ArrayList<>();
    private final Map<PendingAirdropId, Long> pendingFungibleAirdrops = new HashMap<>();
    private final List<TokenID> tokenIds = new ArrayList<>();
    private final Map<TokenID, Long> tokenTotalSupplies = new HashMap<>();
    private final List<TopicID> topicIds = new ArrayList<>();
    private final Map<TopicID, TopicMessage> topicState = new HashMap<>();

    private StateChangeContext() {}

    StateChangeContext(List<StateChanges> stateChangesList) {
        for (var stateChanges : stateChangesList) {
            for (var stateChange : stateChanges.getStateChangesList()) {
                if (!stateChange.hasMapUpdate()) {
                    continue;
                }

                var mapUpdate = stateChange.getMapUpdate();
                switch (stateChange.getStateId()) {
                    case StateIdentifier.STATE_ID_ACCOUNTS_VALUE -> {
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
                    case StateIdentifier.STATE_ID_FILES_VALUE -> fileIds.add(
                            mapUpdate.getKey().getFileIdKey());
                    case StateIdentifier.STATE_ID_NODES_VALUE -> nodeIds.add(
                            mapUpdate.getKey().getEntityNumberKey().getValue());
                    case StateIdentifier.STATE_ID_PENDING_AIRDROPS_VALUE -> {
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
                    case StateIdentifier.STATE_ID_TOKENS_VALUE -> {
                        var token = mapUpdate.getValue().getTokenValue();
                        tokenIds.add(token.getTokenId());
                        tokenTotalSupplies.put(token.getTokenId(), token.getTotalSupply());
                    }
                    case StateIdentifier.STATE_ID_TOPICS_VALUE -> {
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

    public Optional<ContractID> getContractId(ByteString evmAddress) {
        return Optional.ofNullable(contractIds.get(evmAddress));
    }

    public Optional<FileID> getNewFileId() {
        if (fileIds.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(fileIds.removeFirst());
    }

    public Optional<Long> getNewNodeId() {
        if (nodeIds.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(nodeIds.removeFirst());
    }

    public Optional<TokenID> getNewTokenId() {
        if (tokenIds.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(tokenIds.removeFirst());
    }

    public Optional<TopicID> getNewTopicId() {
        if (topicIds.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(topicIds.removeFirst());
    }

    public Optional<TopicMessage> getTopicMessage(TopicID topicId) {
        return Optional.ofNullable(topicState.get(topicId)).map(topicMessage -> {
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

    public Optional<Long> trackTokenTotalSupply(TokenID tokenId, long change) {
        return Optional.ofNullable(tokenTotalSupplies.get(tokenId)).map(totalSupply -> {
            tokenTotalSupplies.put(tokenId, totalSupply + change);
            return totalSupply;
        });
    }
}
