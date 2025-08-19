// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.TokenAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class TokenAirdropTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockItem = blockTransactionTransformation.blockTransaction();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var recordBuilder = blockTransactionTransformation.recordItemBuilder().transactionRecordBuilder();
        var pendingAirdrops = getPendingAirdrops(
                blockTransactionTransformation.getTransactionBody().getTokenAirdrop(),
                blockItem.getTransactionResult());
        for (var pendingAirdrop : pendingAirdrops) {
            var pendingAirdropId = pendingAirdrop.id();
            if (pendingAirdropId.hasFungibleTokenType()) {
                blockItem
                        .getStateChangeContext()
                        .trackPendingFungibleAirdrop(pendingAirdropId, pendingAirdrop.amount())
                        .ifPresentOrElse(
                                amount -> recordBuilder.addNewPendingAirdrops(PendingAirdropRecord.newBuilder()
                                        .setPendingAirdropId(pendingAirdropId)
                                        .setPendingAirdropValue(
                                                PendingAirdropValue.newBuilder().setAmount(amount))),
                                () -> {
                                    log.warn(
                                            "Fungible pending airdrop not found in state at {}",
                                            blockItem.getConsensusTimestamp());
                                    recordBuilder.addNewPendingAirdrops(PendingAirdropRecord.newBuilder()
                                            .setPendingAirdropId(pendingAirdropId)
                                            .setPendingAirdropValue(PendingAirdropValue.newBuilder()
                                                    .setAmount(pendingAirdrop.amount())));
                                });
            } else {
                recordBuilder.addNewPendingAirdrops(
                        PendingAirdropRecord.newBuilder().setPendingAirdropId(pendingAirdropId));
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENAIRDROP;
    }

    /**
     * Get potential token airdrops from the token transfer lists in transaction body and transaction result. Note any
     * credit transfer of a token to an account indicates that there should be no such pending airdrop for the account
     * and the token pair.
     *
     * @param tokenAirdrop
     * @param transactionResult
     * @return A list of pending airdrops. For NFT pending aidrop, the amount is null
     */
    private List<PendingAirdrop> getPendingAirdrops(
            TokenAirdropTransactionBody tokenAirdrop, TransactionResult transactionResult) {
        var tokenCreditsInResult = getTokenCredits(transactionResult);
        var pendingAirdrops = new ArrayList<PendingAirdrop>();
        for (var tokenTransfer : tokenAirdrop.getTokenTransfersList()) {
            AccountID sender = null;
            var tokenId = tokenTransfer.getToken();
            var creditAccountAmounts = new ArrayList<AccountAmount>();

            for (var aa : tokenTransfer.getTransfersList()) {
                if (aa.getAmount() < 0) {
                    // a valid token airdrop transaction should have only one sender for a fungible token
                    sender = aa.getAccountID();
                } else {
                    var tokenCredit = new TokenCredit(aa.getAccountID(), tokenId);
                    if (tokenCreditsInResult.contains(tokenCredit)) {
                        continue;
                    }

                    creditAccountAmounts.add(aa);
                }
            }

            for (var aa : creditAccountAmounts) {
                var pendingAirdropId = PendingAirdropId.newBuilder()
                        .setReceiverId(aa.getAccountID())
                        .setSenderId(sender)
                        .setFungibleTokenType(tokenId)
                        .build();
                pendingAirdrops.add(new PendingAirdrop(aa.getAmount(), pendingAirdropId));
            }

            for (var nftTransfer : tokenTransfer.getNftTransfersList()) {
                var tokenCredit = new TokenCredit(nftTransfer.getReceiverAccountID(), tokenId);
                if (tokenCreditsInResult.contains(tokenCredit)) {
                    continue;
                }

                var pendingAirdropId = PendingAirdropId.newBuilder()
                        .setSenderId(nftTransfer.getSenderAccountID())
                        .setReceiverId(nftTransfer.getReceiverAccountID())
                        .setNonFungibleToken(NftID.newBuilder()
                                .setSerialNumber(nftTransfer.getSerialNumber())
                                .setTokenID(tokenId))
                        .build();
                pendingAirdrops.add(new PendingAirdrop(null, pendingAirdropId));
            }
        }

        return pendingAirdrops;
    }

    private Set<TokenCredit> getTokenCredits(TransactionResult transactionResult) {
        var tokenCredits = new HashSet<TokenCredit>();
        for (var tokenTransfers : transactionResult.getTokenTransferListsList()) {
            var tokenId = tokenTransfers.getToken();

            for (var aa : tokenTransfers.getTransfersList()) {
                if (aa.getAmount() > 0) {
                    tokenCredits.add(new TokenCredit(aa.getAccountID(), tokenId));
                }
            }

            for (var nftTransfer : tokenTransfers.getNftTransfersList()) {
                tokenCredits.add(new TokenCredit(nftTransfer.getReceiverAccountID(), tokenId));
            }
        }

        return tokenCredits;
    }

    private record PendingAirdrop(Long amount, PendingAirdropId id) {}

    private record TokenCredit(AccountID receiver, TokenID tokenId) {}
}
