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

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_PENDING_AIRDROPS;

import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropId.TokenReferenceCase;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Named
final class TokenAirdropTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {
        if (!blockItem.successful()) {
            return;
        }

        var pendingAirdropIds = pendingAirdropsInState(blockItem.stateChanges());
        if (!pendingAirdropIds.isEmpty()) {
            var eligibleAirdropIds =
                    eligibleAirdropIds(transactionBody.getTokenAirdrop().getTokenTransfersList());
            for (var pendingAirdrop : pendingAirdropIds) {
                // Do not add airdrops that could not appear in the transfer list
                if (eligibleAirdropIds.contains(pendingAirdrop.getPendingAirdropId())) {
                    transactionRecordBuilder.addNewPendingAirdrops(pendingAirdrop);
                }
            }
        }

        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasTokenAirdrop()) {
                var output = transactionOutput.getTokenAirdrop();
                var assessedCustomFees = output.getAssessedCustomFeesList();
                transactionRecordBuilder.addAllAssessedCustomFees(assessedCustomFees);
            }
        }
    }

    private Set<PendingAirdropRecord> pendingAirdropsInState(List<StateChanges> stateChangesList) {
        Set<PendingAirdropRecord> pendingAirdropIds = new HashSet<>();
        for (var stateChanges : stateChangesList) {
            for (var stateChange : stateChanges.getStateChangesList()) {
                if (stateChange.getStateId() == STATE_ID_PENDING_AIRDROPS.getNumber()
                        && stateChange.hasMapUpdate()
                        && stateChange.getMapUpdate().getKey().hasPendingAirdropIdKey()
                        && stateChange.getMapUpdate().getValue().hasAccountPendingAirdropValue()) {
                    var mapUpdate = stateChange.getMapUpdate();
                    var pendingId = mapUpdate.getKey().getPendingAirdropIdKey();
                    var pendingAirdrop = PendingAirdropRecord.newBuilder().setPendingAirdropId(pendingId);
                    if (pendingId.getTokenReferenceCase() == TokenReferenceCase.FUNGIBLE_TOKEN_TYPE
                            && mapUpdate
                                    .getValue()
                                    .getAccountPendingAirdropValue()
                                    .hasPendingAirdropValue()) {
                        var accountValue = mapUpdate.getValue().getAccountPendingAirdropValue();
                        pendingAirdrop.setPendingAirdropValue(accountValue.getPendingAirdropValue());
                    }

                    pendingAirdropIds.add(pendingAirdrop.build());
                }
            }
        }

        return pendingAirdropIds;
    }

    private Set<PendingAirdropId> eligibleAirdropIds(List<TokenTransferList> tokenTransfers) {
        var eligibleAirdrops = new HashSet<PendingAirdropId>();
        for (var transfer : tokenTransfers) {
            var tokenId = transfer.getToken();
            eligibleFungiblePendingAirdrops(transfer.getTransfersList(), tokenId, eligibleAirdrops);
            eligibleNftPendingAirdrops(transfer.getNftTransfersList(), tokenId, eligibleAirdrops);
        }

        return eligibleAirdrops;
    }

    @SuppressWarnings("java:S3776")
    private void eligibleFungiblePendingAirdrops(
            List<AccountAmount> accountAmounts, TokenID tokenId, Set<PendingAirdropId> eligibleAirdrops) {
        if (!accountAmounts.isEmpty()) {
            var builder = PendingAirdropId.newBuilder().setFungibleTokenType(tokenId);
            var receivers = new HashSet<AccountID>();
            var senders = new HashSet<AccountID>();
            for (var accountAmount : accountAmounts) {
                if (accountAmount.hasAccountID()) {
                    var accountId = accountAmount.getAccountID();
                    if (accountAmount.getAmount() < 0) {
                        senders.add(accountId);
                    } else {
                        receivers.add(accountId);
                    }
                }
            }

            for (var receiver : receivers) {
                for (var sender : senders) {
                    eligibleAirdrops.add(
                            builder.setReceiverId(receiver).setSenderId(sender).build());
                }
            }
        }
    }

    private void eligibleNftPendingAirdrops(
            List<NftTransfer> nftTransfers, TokenID tokenId, Set<PendingAirdropId> eligibleAirdrops) {
        for (var nftTransfer : nftTransfers) {
            var nftId = NftID.newBuilder().setTokenID(tokenId).setSerialNumber(nftTransfer.getSerialNumber());
            eligibleAirdrops.add(PendingAirdropId.newBuilder()
                    .setNonFungibleToken(nftId)
                    .setReceiverId(nftTransfer.getReceiverAccountID())
                    .setSenderId(nftTransfer.getSenderAccountID())
                    .build());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENAIRDROP;
    }
}
