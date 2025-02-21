/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.domain;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_NFTS;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_NODES;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_PENDING_AIRDROPS;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_SCHEDULES_BY_ID;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS;

import com.google.protobuf.UInt64Value;
import com.hedera.hapi.block.stream.output.protoc.CallContractOutput;
import com.hedera.hapi.block.stream.output.protoc.CreateAccountOutput;
import com.hedera.hapi.block.stream.output.protoc.CreateScheduleOutput;
import com.hedera.hapi.block.stream.output.protoc.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapDeleteChange;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.SignScheduleOutput;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.SubmitMessageOutput;
import com.hedera.hapi.block.stream.output.protoc.TokenAirdropOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.output.protoc.UtilPrngOutput;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AccountPendingAirdrop;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.Schedule;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Topic;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

/**
 * Generates typical protobuf request and response objects with all fields populated.
 */
@Named
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BlockItemBuilder {
    private static final int STATE_FILES_ID = 6;
    private static final int STATE_TOPICS_ID = 0;

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    public BlockItemBuilder.Builder cryptoTransfer() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        return cryptoTransfer(recordItem);
    }

    public BlockItemBuilder.Builder cryptoTransfer(RecordItem recordItem) {
        var contractCallTransactionOutput = TransactionOutput.newBuilder()
                .setContractCall(CallContractOutput.newBuilder()
                        .setContractCallResult(recordItemBuilder.contractFunctionResult())
                        .build())
                .build();
        var cryptoTransferTransactionOutput = TransactionOutput.newBuilder()
                .setCryptoTransfer(CryptoTransferOutput.newBuilder()
                        .addAssessedCustomFees(assessedCustomFees())
                        .build())
                .build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                List.of(contractCallTransactionOutput, cryptoTransferTransactionOutput),
                Collections.emptyList());
    }

    public BlockItemBuilder.Builder scheduleCreate(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var scheduleId = transactionRecord.getReceipt().getScheduleID();
        var transactionId = transactionRecord.getReceipt().getScheduledTransactionID();
        var transactionOutput = TransactionOutput.newBuilder()
                .setCreateSchedule(CreateScheduleOutput.newBuilder()
                        .setScheduleId(scheduleId)
                        .setScheduledTransactionId(transactionId)
                        .build())
                .build();

        var stateChange = StateChange.newBuilder()
                .setStateId(STATE_ID_SCHEDULES_BY_ID.getNumber())
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setScheduleIdKey(scheduleId)))
                .build();
        var stateChanges =
                StateChanges.newBuilder().addStateChanges(stateChange).build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                List.of(transactionOutput),
                List.of(stateChanges));
    }

    public BlockItemBuilder.Builder scheduleDelete(RecordItem recordItem) {
        var scheduleId = recordItem.getTransactionBody().getScheduleDelete().getScheduleID();
        var stateChangeDelete = StateChange.newBuilder()
                .setMapDelete(MapDeleteChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setScheduleIdKey(scheduleId)))
                .build();
        var stateChangeUpdate = StateChange.newBuilder()
                .setStateId(STATE_ID_SCHEDULES_BY_ID.getNumber())
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setValue(MapChangeValue.newBuilder()
                                .setScheduleValue(Schedule.newBuilder()
                                        .setScheduleId(scheduleId)
                                        .build())
                                .build()))
                .build();

        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(stateChangeDelete)
                .addStateChanges(stateChangeUpdate)
                .build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyList(),
                List.of(stateChanges));
    }

    public BlockItemBuilder.Builder scheduleSign(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionId = transactionRecord.getReceipt().getScheduledTransactionID();
        var transactionOutput = TransactionOutput.newBuilder()
                .setSignSchedule(SignScheduleOutput.newBuilder()
                        .setScheduledTransactionId(transactionId)
                        .build())
                .build();
        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                List.of(transactionOutput),
                Collections.emptyList());
    }

    public BlockItemBuilder.Builder utilPrng(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var utilPrngOutputBuilder = UtilPrngOutput.newBuilder();
        if (transactionRecord.hasPrngNumber()) {
            utilPrngOutputBuilder.setPrngNumber(transactionRecord.getPrngNumber());
        } else if (transactionRecord.hasPrngBytes()) {
            utilPrngOutputBuilder.setPrngBytes(transactionRecord.getPrngBytes());
        }
        var transactionOutput = TransactionOutput.newBuilder()
                .setUtilPrng(utilPrngOutputBuilder)
                .build();
        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                List.of(transactionOutput),
                Collections.emptyList());
    }

    public BlockItemBuilder.Builder nodeCreate(RecordItem recordItem) {
        var stateChanges = buildNodeIdStateChanges(recordItem);
        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult(recordItem), List.of(), List.of(stateChanges));
    }

    public BlockItemBuilder.Builder unknown() {
        var recordItem = recordItemBuilder.unknown().build();
        return unknown(recordItem);
    }

    public BlockItemBuilder.Builder unknown(RecordItem recordItem) {
        return defaultBlockItem(recordItem);
    }

    public BlockItemBuilder.Builder defaultBlockItem(RecordItem recordItem) {
        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult(recordItem), List.of(), Collections.emptyList());
    }

    public BlockItemBuilder.Builder cryptoCreate(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var accountId = transactionRecord.getReceipt().getAccountID();
        var transactionOutput = TransactionOutput.newBuilder()
                .setAccountCreate(CreateAccountOutput.newBuilder()
                        .setCreatedAccountId(accountId)
                        .build())
                .build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                List.of(transactionOutput),
                Collections.emptyList());
    }

    public Builder fileCreate(RecordItem recordItem) {
        var stateChanges = buildFileIdStateChanges(recordItem);

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult(recordItem), List.of(), List.of(stateChanges));
    }

    public Builder consensusCreateTopic(RecordItem recordItem) {
        var id = recordItem.getTransactionRecord().getReceipt().getTopicID().getTopicNum();
        var topicId = TopicID.newBuilder().setTopicNum(id).build();
        var key = MapChangeKey.newBuilder().setTopicIdKey(topicId).build();
        var mapUpdate = MapUpdateChange.newBuilder().setKey(key).build();

        var firstChange = StateChange.newBuilder().setStateId(1).build();

        var secondChange = StateChange.newBuilder().setStateId(STATE_TOPICS_ID).build();

        var thirdChange = StateChange.newBuilder()
                .setStateId(STATE_TOPICS_ID)
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .build();

        var fourthChange = StateChange.newBuilder()
                .setStateId(STATE_TOPICS_ID)
                .setMapUpdate(mapUpdate)
                .build();

        var changes = List.of(firstChange, secondChange, thirdChange, fourthChange);
        var stateChanges = StateChanges.newBuilder().addAllStateChanges(changes).build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult(recordItem), List.of(), List.of(stateChanges));
    }

    public Builder consensusSubmitMessage(RecordItem recordItem) {

        var transactionOutput = TransactionOutput.newBuilder()
                .setSubmitMessage(SubmitMessageOutput.newBuilder()
                        .addAssessedCustomFees(assessedCustomFees())
                        .build())
                .build();

        var topicRunningHash = recordItem.getTransactionRecord().getReceipt().getTopicRunningHash();
        var sequenceNumber = recordItem.getTransactionRecord().getReceipt().getTopicSequenceNumber();
        var topicValue = Topic.newBuilder()
                .setRunningHash(topicRunningHash)
                .setSequenceNumber(sequenceNumber)
                .build();
        var value = MapChangeValue.newBuilder().setTopicValue(topicValue).build();
        var mapUpdate = MapUpdateChange.newBuilder().setValue(value).build();

        var firstChange = StateChange.newBuilder().setStateId(1).build();

        var secondChange = StateChange.newBuilder().setStateId(STATE_TOPICS_ID).build();

        var thirdChange = StateChange.newBuilder()
                .setStateId(STATE_TOPICS_ID)
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .build();

        var fourthChange = StateChange.newBuilder()
                .setStateId(STATE_TOPICS_ID)
                .setMapUpdate(mapUpdate)
                .build();

        var changes = List.of(firstChange, secondChange, thirdChange, fourthChange);
        var stateChanges = StateChanges.newBuilder().addAllStateChanges(changes).build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                List.of(transactionOutput),
                List.of(stateChanges));
    }

    public Builder tokenAirdrop(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var pendingAirdrops = transactionRecord.getNewPendingAirdropsList();
        List<StateChange> changes = new ArrayList<>();
        for (var pendingAirdrop : pendingAirdrops) {
            var accountPendingAirdrop =
                    AccountPendingAirdrop.newBuilder().setPendingAirdropValue(pendingAirdrop.getPendingAirdropValue());
            changes.add(StateChange.newBuilder()
                    .setStateId(STATE_ID_PENDING_AIRDROPS.getNumber())
                    .setMapUpdate(MapUpdateChange.newBuilder()
                            .setKey(MapChangeKey.newBuilder()
                                    .setPendingAirdropIdKey(pendingAirdrop.getPendingAirdropId()))
                            .setValue(MapChangeValue.newBuilder()
                                    .setAccountPendingAirdropValue(accountPendingAirdrop)
                                    .build())
                            .build())
                    .build());
        }

        // Add state changes that are not reflected in the possible state changes from the transaction body
        changes.add(StateChange.newBuilder()
                .setStateId(STATE_ID_PENDING_AIRDROPS.getNumber())
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setPendingAirdropIdKey(PendingAirdropId.newBuilder()
                                        .setFungibleTokenType(recordItemBuilder.tokenId())
                                        .setReceiverId(recordItemBuilder.accountId())
                                        .setSenderId(recordItemBuilder.accountId())
                                        .build()))
                        .setValue(MapChangeValue.newBuilder()
                                .setAccountPendingAirdropValue(AccountPendingAirdrop.newBuilder()
                                        .setPendingAirdropValue(PendingAirdropValue.newBuilder()
                                                .setAmount(1)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        changes.add(StateChange.newBuilder()
                .setStateId(STATE_ID_PENDING_AIRDROPS.getNumber())
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setPendingAirdropIdKey(PendingAirdropId.newBuilder()
                                        .setNonFungibleToken(NftID.newBuilder()
                                                .setTokenID(recordItemBuilder.tokenId())
                                                .setSerialNumber(5000)
                                                .build())
                                        .setReceiverId(recordItemBuilder.accountId())
                                        .setSenderId(recordItemBuilder.accountId())
                                        .build()))
                        .build())
                .build());

        var stateChanges = StateChanges.newBuilder().addAllStateChanges(changes).build();

        var transactionOutput = TransactionOutput.newBuilder()
                .setTokenAirdrop(TokenAirdropOutput.newBuilder()
                        .addAssessedCustomFees(assessedCustomFees())
                        .build())
                .build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                List.of(transactionOutput),
                List.of(stateChanges));
    }

    public Builder tokenBurn(RecordItem recordItem) {
        return tokenSupplyStateChanges(recordItem, List.of());
    }

    public Builder tokenCreate(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var receipt = transactionRecord.getReceipt();
        var tokenId = receipt.getTokenID();
        var stateChange = StateChange.newBuilder()
                .setStateId(STATE_ID_TOKENS.getNumber())
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                        .build())
                .build();
        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyList(),
                List.of(StateChanges.newBuilder().addStateChanges(stateChange).build()));
    }

    public Builder tokenMint(RecordItem recordItem) {
        var serialNumbers = recordItem.getTransactionRecord().getReceipt().getSerialNumbersList();
        return tokenSupplyStateChanges(recordItem, serialNumbers);
    }

    public Builder tokenWipe(RecordItem recordItem) {
        var serialNumbers = recordItem.getTransactionBody().getTokenWipe().getSerialNumbersList();
        return tokenSupplyStateChanges(recordItem, serialNumbers);
    }

    private Builder tokenSupplyStateChanges(RecordItem recordItem, List<Long> serialNumbers) {
        var receipt = recordItem.getTransactionRecord().getReceipt();
        var tokenId = receipt.getTokenID();
        var stateChangesBuilder =
                StateChanges.newBuilder().addStateChanges(getNewSupplyState(tokenId, receipt.getNewTotalSupply()));
        stateChangesBuilder.addAllStateChanges(getSerialNumbersStateChanges(serialNumbers, tokenId));
        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyList(),
                List.of(stateChangesBuilder.build()));
    }

    private List<StateChange> getSerialNumbersStateChanges(List<Long> serialNumbers, TokenID tokenId) {
        return serialNumbers.stream()
                .map(serialNumber -> MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder()
                                .setNftIdKey(NftID.newBuilder()
                                        .setTokenID(tokenId)
                                        .setSerialNumber(serialNumber)
                                        .build()))
                        .build())
                .map(mapUpdate -> StateChange.newBuilder()
                        .setStateId(STATE_ID_NFTS.getNumber())
                        .setMapUpdate(mapUpdate)
                        .build())
                .toList();
    }

    private StateChange getNewSupplyState(TokenID tokenId, long newTotalSupply) {
        return StateChange.newBuilder()
                .setStateId(STATE_ID_TOKENS.getNumber())
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setTokenIdKey(tokenId))
                        .setValue(MapChangeValue.newBuilder()
                                .setTokenValue(Token.newBuilder().setTotalSupply(newTotalSupply)))
                        .build())
                .build();
    }

    private static StateChanges buildFileIdStateChanges(RecordItem recordItem) {
        var id = recordItem.getTransactionRecord().getReceipt().getFileID().getFileNum();
        var fileId = FileID.newBuilder().setFileNum(id).build();
        var key = MapChangeKey.newBuilder().setFileIdKey(fileId).build();
        var mapUpdate = MapUpdateChange.newBuilder().setKey(key).build();

        var firstChange = StateChange.newBuilder()
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .setStateId(1)
                .build();

        var secondChange = StateChange.newBuilder().setStateId(STATE_FILES_ID).build();

        var thirdChange = StateChange.newBuilder()
                .setStateId(STATE_FILES_ID)
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .build();

        var fourtChange = StateChange.newBuilder()
                .setMapUpdate(mapUpdate)
                .setStateId(STATE_FILES_ID)
                .build();

        var changes = List.of(firstChange, secondChange, thirdChange, fourtChange);

        return StateChanges.newBuilder().addAllStateChanges(changes).build();
    }

    private static StateChanges buildNodeIdStateChanges(RecordItem recordItem) {
        var nodeId = recordItem.getTransactionRecord().getReceipt().getNodeId();
        var key = MapChangeKey.newBuilder()
                .setEntityNumberKey(UInt64Value.of(nodeId))
                .build();
        var mapUpdate = MapUpdateChange.newBuilder().setKey(key).build();

        var firstChange = StateChange.newBuilder()
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .setStateId(1)
                .build();

        var secondChange =
                StateChange.newBuilder().setStateId(STATE_ID_NODES.getNumber()).build();

        var thirdChange = StateChange.newBuilder()
                .setStateId(STATE_ID_NODES.getNumber())
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .build();

        var fourthChange = StateChange.newBuilder()
                .setMapUpdate(mapUpdate)
                .setStateId(STATE_ID_NODES.getNumber())
                .build();

        var changes = List.of(firstChange, secondChange, thirdChange, fourthChange);
        return StateChanges.newBuilder().addAllStateChanges(changes).build();
    }

    private AssessedCustomFee.Builder assessedCustomFees() {
        return AssessedCustomFee.newBuilder()
                .setAmount(1L)
                .addEffectivePayerAccountId(recordItemBuilder.accountId())
                .setFeeCollectorAccountId(recordItemBuilder.accountId())
                .setTokenId(recordItemBuilder.tokenId());
    }

    private Timestamp timestamp(long consensusTimestamp) {
        var instant = Instant.ofEpochSecond(0, consensusTimestamp);
        return Utility.instantToTimestamp(instant);
    }

    private TransactionResult transactionResult(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var timestamp = timestamp(recordItem.getConsensusTimestamp());
        return transactionResult(transactionRecord, timestamp).build();
    }

    private TransactionResult.Builder transactionResult(
            TransactionRecord transactionRecord, Timestamp consensusTimestamp) {
        var builder = TransactionResult.newBuilder();
        if (transactionRecord.hasParentConsensusTimestamp()) {
            builder.setParentConsensusTimestamp(transactionRecord.getParentConsensusTimestamp());
        }
        if (transactionRecord.hasScheduleRef()) {
            builder.setScheduleRef(transactionRecord.getScheduleRef());
        }

        return builder.addAllPaidStakingRewards(transactionRecord.getPaidStakingRewardsList())
                .addAllAutomaticTokenAssociations(transactionRecord.getAutomaticTokenAssociationsList())
                .addAllTokenTransferLists(transactionRecord.getTokenTransferListsList())
                .setConsensusTimestamp(consensusTimestamp)
                .setTransferList(transactionRecord.getTransferList())
                .setTransactionFeeCharged(transactionRecord.getTransactionFee())
                .setStatus(transactionRecord.getReceipt().getStatus());
    }

    public class Builder {
        private final Transaction transaction;
        private final List<TransactionOutput> transactionOutputs;
        private final TransactionResult transactionResult;
        private final List<StateChanges> stateChanges;

        private Builder(
                Transaction transaction,
                TransactionResult transactionResult,
                List<TransactionOutput> transactionOutputs,
                List<StateChanges> stateChanges) {
            this.stateChanges = stateChanges;
            this.transaction = transaction;
            this.transactionOutputs = transactionOutputs;
            this.transactionResult = transactionResult;
        }

        public BlockItem build() {
            return BlockItem.builder()
                    .transaction(transaction)
                    .transactionResult(transactionResult)
                    .transactionOutput(transactionOutputs)
                    .stateChanges(stateChanges)
                    .build();
        }
    }
}
