// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.AbstractCryptoAllowance;
import org.hiero.mirror.common.domain.entity.AbstractNftAllowance;
import org.hiero.mirror.common.domain.entity.AbstractTokenAllowance;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.token.AbstractNft;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.contractlog.ApproveAllowanceContractLog;
import org.hiero.mirror.importer.parser.contractlog.ApproveAllowanceIndexedContractLog;
import org.hiero.mirror.importer.parser.contractlog.ApproveForAllAllowanceContractLog;
import org.hiero.mirror.importer.parser.contractlog.SyntheticContractLogService;
import org.hiero.mirror.importer.parser.contractresult.ApproveAllowanceContractResult;
import org.hiero.mirror.importer.parser.contractresult.SyntheticContractResultService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@CustomLog
@Named
@RequiredArgsConstructor
class CryptoApproveAllowanceTransactionHandler extends AbstractTransactionHandler {

    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final SyntheticContractLogService syntheticContractLogService;
    private final SyntheticContractResultService syntheticContractResultService;

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOAPPROVEALLOWANCE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getCryptoApproveAllowance();
        parseCryptoAllowances(transactionBody.getCryptoAllowancesList(), recordItem);
        parseNftAllowances(transactionBody.getNftAllowancesList(), recordItem);
        parseTokenAllowances(transactionBody.getTokenAllowancesList(), recordItem);

        if (transactionBody.getTokenAllowancesCount() > 0 || transactionBody.getNftAllowancesCount() > 0) {
            syntheticContractResultService.create(
                    new ApproveAllowanceContractResult(recordItem, EntityId.EMPTY, recordItem.getPayerAccountId()));
        }
    }

    private void parseCryptoAllowances(
            List<com.hederahashgraph.api.proto.java.CryptoAllowance> cryptoAllowances, RecordItem recordItem) {
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        var cryptoAllowanceState = new HashMap<AbstractCryptoAllowance.Id, CryptoAllowance>();
        var payerAccountId = recordItem.getPayerAccountId();

        // iterate the crypto allowance list in reverse order and honor the last allowance for the same owner and
        // spender
        var iterator = cryptoAllowances.listIterator(cryptoAllowances.size());
        while (iterator.hasPrevious()) {
            var cryptoApproval = iterator.previous();
            var ownerAccountId = getOwnerAccountId(cryptoApproval.getOwner(), payerAccountId);
            if (EntityId.isEmpty(ownerAccountId)) {
                Utility.handleRecoverableError("Empty ownerAccountId at consensusTimestamp {}", consensusTimestamp);
                continue;
            }

            var cryptoAllowance = new CryptoAllowance();
            var spender = EntityId.of(cryptoApproval.getSpender());
            cryptoAllowance.setAmountGranted(cryptoApproval.getAmount());
            cryptoAllowance.setAmount(cryptoApproval.getAmount());
            cryptoAllowance.setOwner(ownerAccountId.getId());
            cryptoAllowance.setPayerAccountId(payerAccountId);
            cryptoAllowance.setSpender(spender.getId());
            cryptoAllowance.setTimestampLower(consensusTimestamp);

            if (cryptoAllowanceState.putIfAbsent(cryptoAllowance.getId(), cryptoAllowance) == null) {
                entityListener.onCryptoAllowance(cryptoAllowance);
                recordItem.addEntityId(ownerAccountId);
                recordItem.addEntityId(spender);
            }
        }
    }

    private void parseNftAllowances(
            List<com.hederahashgraph.api.proto.java.NftAllowance> nftAllowances, RecordItem recordItem) {
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        var payerAccountId = recordItem.getPayerAccountId();
        var nftAllowanceState = new HashMap<AbstractNftAllowance.Id, NftAllowance>();
        var nftSerialAllowanceState = new HashMap<AbstractNft.Id, Nft>();
        // iterate the nft allowance list in reverse order and honor the last allowance for either
        // the same owner, spender, and token for approved for all allowances, or the last serial allowance for
        // the same owner, spender, token, and serial
        var iterator = nftAllowances.listIterator(nftAllowances.size());
        while (iterator.hasPrevious()) {
            var nftApproval = iterator.previous();
            var ownerAccountId = getOwnerAccountId(nftApproval.getOwner(), payerAccountId);
            if (EntityId.isEmpty(ownerAccountId)) {
                // ownerAccountId will be EMPTY only when getOwnerAccountId fails to resolve the owner in the alias form
                continue;
            }

            var spenderId = EntityId.of(nftApproval.getSpender());
            var tokenId = EntityId.of(nftApproval.getTokenId());
            boolean hasApprovedForAll = nftApproval.hasApprovedForAll();
            parseNftApproveForAll(
                    recordItem, nftAllowanceState, nftApproval, ownerAccountId, spenderId, tokenId, hasApprovedForAll);

            var delegatingSpenderId = EntityId.of(nftApproval.getDelegatingSpender());
            var delegatingSpender = EntityId.isEmpty(delegatingSpenderId) ? null : delegatingSpenderId.getId();
            for (var serialNumber : nftApproval.getSerialNumbersList()) {
                // services allows the same serial number of a nft token appears in multiple nft allowances to
                // different spenders. The last spender will be granted such allowance.
                var nft = Nft.builder()
                        .accountId(ownerAccountId)
                        .delegatingSpender(delegatingSpender)
                        .serialNumber(serialNumber)
                        .spender(spenderId.getId())
                        .timestampRange(Range.atLeast(consensusTimestamp))
                        .tokenId(tokenId.getId())
                        .build();

                if (nftSerialAllowanceState.putIfAbsent(nft.getId(), nft) == null) {
                    entityListener.onNft(nft);
                    if (!hasApprovedForAll) {
                        syntheticContractLogService.create(new ApproveAllowanceIndexedContractLog(
                                recordItem, tokenId, ownerAccountId, spenderId, serialNumber));
                    }
                }
            }

            recordItem.addEntityId(delegatingSpenderId);
            recordItem.addEntityId(ownerAccountId);
            recordItem.addEntityId(spenderId);
            recordItem.addEntityId(tokenId);
        }
    }

    private void parseNftApproveForAll(
            RecordItem recordItem,
            HashMap<AbstractNftAllowance.Id, NftAllowance> nftAllowanceState,
            com.hederahashgraph.api.proto.java.NftAllowance nftApproval,
            EntityId ownerAccountId,
            EntityId spender,
            EntityId tokenId,
            boolean hasApprovedForAll) {
        if (!hasApprovedForAll) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var payerAccountId = recordItem.getPayerAccountId();

        boolean approvedForAll = nftApproval.getApprovedForAll().getValue();
        var nftAllowance = new NftAllowance();
        nftAllowance.setApprovedForAll(approvedForAll);
        nftAllowance.setOwner(ownerAccountId.getId());
        nftAllowance.setPayerAccountId(payerAccountId);
        nftAllowance.setSpender(spender.getId());
        nftAllowance.setTokenId(tokenId.getId());
        nftAllowance.setTimestampLower(consensusTimestamp);

        if (nftAllowanceState.putIfAbsent(nftAllowance.getId(), nftAllowance) == null) {
            entityListener.onNftAllowance(nftAllowance);
            syntheticContractLogService.create(new ApproveForAllAllowanceContractLog(
                    recordItem, tokenId, ownerAccountId, spender, approvedForAll));

            recordItem.addEntityId(ownerAccountId);
            recordItem.addEntityId(spender);
            recordItem.addEntityId(tokenId);
        }
    }

    private void parseTokenAllowances(
            List<com.hederahashgraph.api.proto.java.TokenAllowance> tokenAllowances, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var payerAccountId = recordItem.getPayerAccountId();
        var tokenAllowanceState = new HashMap<AbstractTokenAllowance.Id, TokenAllowance>();
        // iterate the token allowance list in reverse order and honor the last allowance for the same owner, spender,
        // and token
        var iterator = tokenAllowances.listIterator(tokenAllowances.size());
        while (iterator.hasPrevious()) {
            var tokenApproval = iterator.previous();
            var ownerAccountId = getOwnerAccountId(tokenApproval.getOwner(), payerAccountId);
            if (EntityId.isEmpty(ownerAccountId)) {
                // ownerAccountId will be EMPTY only when getOwnerAccountId fails to resolve the owner in the alias form
                continue;
            }
            var spenderId = EntityId.of(tokenApproval.getSpender());
            var tokenId = EntityId.of(tokenApproval.getTokenId());

            var tokenAllowance = new TokenAllowance();
            tokenAllowance.setAmountGranted(tokenApproval.getAmount());
            tokenAllowance.setAmount(tokenApproval.getAmount());
            tokenAllowance.setOwner(ownerAccountId.getId());
            tokenAllowance.setPayerAccountId(payerAccountId);
            tokenAllowance.setSpender(spenderId.getId());
            tokenAllowance.setTokenId(tokenId.getId());
            tokenAllowance.setTimestampLower(consensusTimestamp);

            if (tokenAllowanceState.putIfAbsent(tokenAllowance.getId(), tokenAllowance) == null) {
                entityListener.onTokenAllowance(tokenAllowance);
                syntheticContractLogService.create(new ApproveAllowanceContractLog(
                        recordItem, tokenId, ownerAccountId, spenderId, tokenApproval.getAmount()));

                recordItem.addEntityId(ownerAccountId);
                recordItem.addEntityId(spenderId);
                recordItem.addEntityId(tokenId);
            }
        }
    }

    /**
     * Gets the owner of the allowance. An empty owner in the *Allowance protobuf message implies the transaction payer
     * is the owner of the resource the spender is granted allowance of.
     *
     * @param owner          The owner in the *Allowance protobuf message
     * @param payerAccountId The transaction payer
     * @return The effective owner account id
     */
    private EntityId getOwnerAccountId(AccountID owner, EntityId payerAccountId) {
        var entityId = entityIdService.lookup(owner);
        if (entityId.isPresent()) {
            var ownerAccountId = entityId.get();
            return !EntityId.isEmpty(ownerAccountId) ? ownerAccountId : payerAccountId;
        }

        return EntityId.EMPTY;
    }
}
