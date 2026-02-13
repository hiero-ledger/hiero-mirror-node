// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.hiero.mirror.common.domain.token.NftTransfer.WILDCARD_SERIAL_NUMBER;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.token.DissociateTokenTransfer;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.ErrataType;
import org.hiero.mirror.common.domain.transaction.ItemizedTransfer;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.StakingRewardTransfer;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.exception.InvalidEntityException;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.ContractResultService;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.domain.TransactionFilterFields;
import org.hiero.mirror.importer.exception.ImporterException;
import org.hiero.mirror.importer.parser.CommonParserProperties;
import org.hiero.mirror.importer.parser.contractlog.SyntheticContractLogService;
import org.hiero.mirror.importer.parser.contractlog.TransferContractLog;
import org.hiero.mirror.importer.parser.contractlog.TransferIndexedContractLog;
import org.hiero.mirror.importer.parser.contractresult.SyntheticContractResultService;
import org.hiero.mirror.importer.parser.contractresult.TransferContractResult;
import org.hiero.mirror.importer.parser.record.RecordItemListener;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import org.hiero.mirror.importer.util.Utility;

@CustomLog
@Named
@ConditionOnEntityRecordParser
@RequiredArgsConstructor
public class EntityRecordItemListener implements RecordItemListener {

    private final CommonParserProperties commonParserProperties;
    private final ContractResultService contractResultService;
    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final TransactionHandlerFactory transactionHandlerFactory;
    private final SyntheticContractLogService syntheticContractLogService;
    private final SyntheticContractResultService syntheticContractResultService;
    private final TransferEventsGenerator transferEventsGenerator;

    @Override
    public void onItem(RecordItem recordItem) throws ImporterException {
        recordItem.setEntityTransactionPredicate(entityProperties.getPersist()::shouldPersistEntityTransaction);
        recordItem.setContractTransactionPredicate(
                entityId -> entityProperties.getPersist().isContractTransaction());

        int transactionTypeValue = recordItem.getTransactionType();
        TransactionType transactionType = TransactionType.of(transactionTypeValue);
        TransactionHandler transactionHandler = transactionHandlerFactory.get(transactionType);

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        EntityId entityId;
        try {
            entityId = transactionHandler.getEntity(recordItem);
        } catch (InvalidEntityException e) { // transaction can have invalid topic/contract/file id
            if (!recordItem.isInvalidIdError()) {
                Utility.handleRecoverableError(
                        "Invalid entity encountered for consensusTimestamp {} : {}",
                        consensusTimestamp,
                        e.getMessage());
            }
            entityId = EntityId.EMPTY;
        }

        // to:do - exclude Freeze from Filter transaction type
        TransactionFilterFields transactionFilterFields = getTransactionFilterFields(entityId, recordItem);
        Collection<EntityId> entities = transactionFilterFields.getEntities();
        log.debug("Processing {} transaction {} for entities {}", transactionType, consensusTimestamp, entities);
        if (!commonParserProperties.getFilter().test(transactionFilterFields)) {
            log.debug(
                    "Ignoring transaction. consensusTimestamp={}, transactionType={}, entities={}",
                    consensusTimestamp,
                    transactionType,
                    entities);
            return;
        }

        Transaction transaction = buildTransaction(entityId, recordItem);
        transactionHandler.updateTransaction(transaction, recordItem);

        // Insert transfers even on failure
        insertTransferList(recordItem);
        insertStakingRewardTransfers(recordItem);

        // handle scheduled transaction, even on failure
        if (transaction.isScheduled()) {
            onScheduledTransaction(recordItem);
        }

        if (recordItem.isSuccessful()) {
            if (entityProperties.getPersist().getTransactionSignatures().contains(transactionType)) {
                insertTransactionSignatures(
                        transaction.getEntityId(),
                        recordItem.getConsensusTimestamp(),
                        recordItem.getSignatureMap().getSigPairList());
            }

            // Only add non-fee transfers on success as the data is assured to be valid
            processItemizedTransfers(recordItem, transaction);
        }

        // Errata records can fail with FAIL_INVALID but still have items in the record committed to state.
        if (recordItem.isSuccessful() || recordItem.getTransactionStatus() == ResponseCodeEnum.FAIL_INVALID_VALUE) {
            insertAutomaticTokenAssociations(recordItem);
            // Record token transfers can be populated for multiple transaction types
            insertTokenTransfers(recordItem, transaction);
            insertAssessedCustomFees(recordItem);
        }

        contractResultService.process(recordItem, transaction);

        var entityTransactions = recordItem.getEntityTransactions();
        if (!entityTransactions.isEmpty()) {
            entityListener.onEntityTransactions(entityTransactions.values());
        }
        var contractTransactions = recordItem.populateContractTransactions();
        if (!contractTransactions.isEmpty()) {
            entityListener.onContractTransactions(contractTransactions);
        }
        entityListener.onTransaction(transaction);
        log.debug("Storing transaction: {}", transaction);
    }

    private Transaction buildTransaction(EntityId entityId, RecordItem recordItem) {
        TransactionBody body = recordItem.getTransactionBody();
        TransactionRecord txRecord = recordItem.getTransactionRecord();

        Long validDurationSeconds = body.hasTransactionValidDuration()
                ? body.getTransactionValidDuration().getSeconds()
                : null;
        // transactions in stream always have valid node account id.
        var nodeAccount = EntityId.of(body.getNodeAccountID());
        var transactionId = body.getTransactionID();

        // build transaction
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(txRecord.getTransactionFee());
        transaction.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        transaction.setEntityId(entityId);
        transaction.setHighVolume(body.getHighVolume());
        transaction.setIndex(recordItem.getTransactionIndex());
        transaction.setInitialBalance(0L);
        transaction.setMaxCustomFees(getMaxCustomFees(body, recordItem));
        transaction.setMaxFee(body.getTransactionFee());
        transaction.setMemo(DomainUtils.toBytes(body.getMemoBytes()));
        transaction.setNodeAccountId(nodeAccount);
        transaction.setNonce(transactionId.getNonce());
        transaction.setPayerAccountId(recordItem.getPayerAccountId());
        transaction.setResult(txRecord.getReceipt().getStatusValue());
        transaction.setScheduled(txRecord.hasScheduleRef());
        transaction.setTransactionBytes(
                entityProperties.getPersist().isTransactionBytes()
                        ? recordItem.getTransaction().toByteArray()
                        : null);
        transaction.setTransactionHash(DomainUtils.toBytes(txRecord.getTransactionHash()));
        transaction.setTransactionRecordBytes(
                entityProperties.getPersist().isTransactionRecordBytes()
                        ? recordItem.getTransactionRecord().toByteArray()
                        : null);
        transaction.setType(recordItem.getTransactionType());
        transaction.setValidDurationSeconds(validDurationSeconds);
        transaction.setValidStartNs(DomainUtils.timeStampInNanos(transactionId.getTransactionValidStart()));

        if (txRecord.hasParentConsensusTimestamp()) {
            transaction.setParentConsensusTimestamp(
                    DomainUtils.timestampInNanosMax(txRecord.getParentConsensusTimestamp()));
        }

        if (body.hasBatchKey()) {
            transaction.setBatchKey(body.getBatchKey().toByteArray());
        }

        return transaction;
    }

    /**
     * Store transfers in the transactions.itemized_transfers column if applicable. This will allow the rest-api to
     * create an itemized set of transfers that reflects explicit transfers, threshold records, node fee, and
     * network+service fee (paid to treasury).
     */
    private void processItemizedTransfers(RecordItem recordItem, Transaction transaction) {
        if (!(entityProperties.getPersist().isItemizedTransfers()
                || entityProperties.getPersist().isTrackAllowance())) {
            return;
        }

        var body = recordItem.getTransactionBody();
        if (!body.hasCryptoTransfer()) {
            return;
        }

        var payerAccount = recordItem.getPayerAccountId();
        var transfers = body.getCryptoTransfer().getTransfers().getAccountAmountsList();
        for (var aa : transfers) {
            var entityId = entityIdService.lookup(aa.getAccountID()).orElse(EntityId.EMPTY);
            if (EntityId.isEmpty(entityId)) {
                Utility.handleRecoverableError(
                        "Invalid itemizedTransfer entity id at {}", recordItem.getConsensusTimestamp());
                continue;
            }

            if (entityProperties.getPersist().isItemizedTransfers()) {
                var itemizedTransfer = new ItemizedTransfer();
                itemizedTransfer.setAmount(aa.getAmount());
                itemizedTransfer.setEntityId(entityId);
                itemizedTransfer.setIsApproval(aa.getIsApproval());
                transaction.addItemizedTransfer(itemizedTransfer);
                recordItem.addEntityId(entityId);
            }

            // Emit allowance amount representing an approved transfer debit
            if (entityProperties.getPersist().isTrackAllowance() && aa.getIsApproval() && aa.getAmount() < 0) {
                var cryptoAllowance = CryptoAllowance.builder()
                        .amount(aa.getAmount())
                        .owner(entityId.getId())
                        .payerAccountId(payerAccount)
                        .spender(payerAccount.getId())
                        .build();
                entityListener.onCryptoAllowance(cryptoAllowance);
            }
        }
    }

    private void insertStakingRewardTransfers(RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var payerAccountId = recordItem.getPayerAccountId();

        for (var aa : recordItem.getTransactionRecord().getPaidStakingRewardsList()) {
            var accountId = EntityId.of(aa.getAccountID());
            var stakingRewardTransfer = new StakingRewardTransfer();
            stakingRewardTransfer.setAccountId(accountId.getId());
            stakingRewardTransfer.setAmount(aa.getAmount());
            stakingRewardTransfer.setConsensusTimestamp(consensusTimestamp);
            stakingRewardTransfer.setPayerAccountId(payerAccountId);

            entityListener.onStakingRewardTransfer(stakingRewardTransfer);
            recordItem.addEntityId(accountId);
        }
    }

    /*
     * Extracts crypto transfers from the record. The extra logic around 'failedTransfer' is to detect and remove
     * spurious non-fee transfers that occurred due to a services bug in the past as documented in
     * ErrataMigration.spuriousTransfers().
     */
    private void insertTransferList(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        if (!transactionRecord.hasTransferList()
                || !entityProperties.getPersist().isCryptoTransferAmounts()) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var transferList = transactionRecord.getTransferList();
        EntityId payerAccountId = recordItem.getPayerAccountId();
        var body = recordItem.getTransactionBody();
        boolean failedTransfer =
                !recordItem.isSuccessful() && body.hasCryptoTransfer() && consensusTimestamp < 1577836799000000000L;

        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var account = EntityId.of(aa.getAccountID());
            CryptoTransfer cryptoTransfer = new CryptoTransfer();
            cryptoTransfer.setAmount(aa.getAmount());
            cryptoTransfer.setConsensusTimestamp(consensusTimestamp);
            cryptoTransfer.setEntityId(account.getId());
            cryptoTransfer.setIsApproval(false);
            cryptoTransfer.setPayerAccountId(payerAccountId);

            AccountAmount accountAmountInsideBody = null;
            if (cryptoTransfer.getAmount() < 0 || failedTransfer) {
                accountAmountInsideBody = findAccountAmount(aa, body);
            }

            if (accountAmountInsideBody != null) {
                cryptoTransfer.setIsApproval(accountAmountInsideBody.getIsApproval());
                if (failedTransfer) {
                    cryptoTransfer.setErrata(ErrataType.DELETE);
                }
            }

            entityListener.onCryptoTransfer(cryptoTransfer);
            recordItem.addEntityId(account);
        }
    }

    private AccountAmount findAccountAmount(AccountAmount aa, TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return null;
        }
        List<AccountAmount> accountAmountsList =
                body.getCryptoTransfer().getTransfers().getAccountAmountsList();
        for (AccountAmount a : accountAmountsList) {
            if (aa.getAmount() == a.getAmount() && aa.getAccountID().equals(a.getAccountID())) {
                return a;
            }
        }
        return null;
    }

    private AccountAmount findAccountAmount(
            Predicate<AccountAmount> accountAmountPredicate, EntityId tokenId, TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return null;
        }
        List<TokenTransferList> tokenTransfersLists = body.getCryptoTransfer().getTokenTransfersList();
        for (TokenTransferList transferList : tokenTransfersLists) {
            if (!EntityId.of(transferList.getToken()).equals(tokenId)) {
                continue;
            }
            for (AccountAmount aa : transferList.getTransfersList()) {
                if (accountAmountPredicate.test(aa)) {
                    return aa;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("java:S1168")
    private byte[][] getMaxCustomFees(TransactionBody body, RecordItem recordItem) {
        int count = body.getMaxCustomFeesCount();
        if (count == 0) {
            return null;
        }

        var maxCustomFees = new byte[count][];
        for (int i = 0; i < count; i++) {
            var maxCustomFee = body.getMaxCustomFees(i);
            maxCustomFees[i] = maxCustomFee.toByteArray();
            recordItem.addEntityId(EntityId.of(maxCustomFee.getAccountId()));
            for (var fixedFee : maxCustomFee.getFeesList()) {
                recordItem.addEntityId(EntityId.of(fixedFee.getDenominatingTokenId()));
            }
        }

        return maxCustomFees;
    }

    private void insertFungibleTokenTransfers(RecordItem recordItem, TokenTransferList tokenTransferList) {
        if (tokenTransferList.getTransfersList().isEmpty()) {
            return;
        }

        var body = recordItem.getTransactionBody();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        boolean isTokenDissociate = body.hasTokenDissociate();
        var payerAccountId = recordItem.getPayerAccountId();
        var tokenId = EntityId.of(tokenTransferList.getToken());
        var tokenTransfers = tokenTransferList.getTransfersList();
        int tokenTransferCount = tokenTransfers.size();

        boolean isDeletedTokenDissociate = isTokenDissociate && tokenTransferCount == 1;

        boolean isWipeOrBurn = recordItem.getTransactionType() == TransactionType.TOKENBURN.getProtoId()
                || recordItem.getTransactionType() == TransactionType.TOKENWIPE.getProtoId();
        boolean isMint = recordItem.getTransactionType() == TransactionType.TOKENMINT.getProtoId()
                || recordItem.getTransactionType() == TransactionType.TOKENCREATION.getProtoId();
        var record = recordItem.getTransactionRecord();
        boolean isContractOrigin = record.hasContractCallResult() || record.hasContractCreateResult();
        EntityId contractId = null;
        if (record.hasContractCallResult()) {
            contractId = EntityId.of(record.getContractCallResult().getContractID());
        } else if (record.hasContractCreateResult()) {
            contractId = EntityId.of(record.getContractCreateResult().getContractID());
        }

        for (int i = 0; i < tokenTransferCount; i++) {
            AccountAmount accountAmount = tokenTransfers.get(i);
            EntityId accountId = EntityId.of(accountAmount.getAccountID());
            long amount = accountAmount.getAmount();
            var tokenTransfer = isDeletedTokenDissociate ? new DissociateTokenTransfer() : new TokenTransfer();
            tokenTransfer.setAmount(amount);
            tokenTransfer.setId(new TokenTransfer.Id(consensusTimestamp, tokenId, accountId));
            tokenTransfer.setIsApproval(false);
            tokenTransfer.setPayerAccountId(payerAccountId);

            handleNegativeAccountAmounts(tokenId, body, accountAmount, amount, tokenTransfer);
            entityListener.onTokenTransfer(tokenTransfer);
            recordItem.addEntityId(accountId);
            recordItem.addEntityId(tokenId);

            if (isDeletedTokenDissociate) {
                // for the token transfer of a deleted token in a token dissociate transaction, the amount is negative
                // to bring the account's balance of the token to 0. Set the totalSupply of the token object to the
                // negative amount, later in the pipeline the token total supply will be reduced accordingly
                Token token = new Token();
                token.setTokenId(tokenId.getId());
                token.setTotalSupply(accountAmount.getAmount());
                entityListener.onToken(token);
            }

            logTokenEvents(recordItem, tokenId, isWipeOrBurn, isMint, accountId, amount);
        }

        transferEventsGenerator.generate(recordItem, isContractOrigin ? contractId : tokenId, tokenTransfers);
    }

    private boolean isApprovalNftTransfer(NftTransfer nftTransfer, TokenID tokenId, TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return false;
        }

        var tokenTransfersList = body.getCryptoTransfer().getTokenTransfersList();
        for (var transferList : tokenTransfersList) {
            if (!transferList.getToken().equals(tokenId)) {
                continue;
            }

            for (var transfer : transferList.getNftTransfersList()) {
                if (transfer.getSerialNumber() == nftTransfer.getSerialNumber()
                        && transfer.getReceiverAccountID().equals(nftTransfer.getReceiverAccountID())
                        && transfer.getSenderAccountID().equals(nftTransfer.getSenderAccountID())) {
                    return transfer.getIsApproval();
                }
            }
        }

        return false;
    }

    private void logTokenEvents(
            RecordItem recordItem,
            EntityId tokenId,
            boolean isWipeOrBurn,
            boolean isMint,
            EntityId accountId,
            long amount) {
        if (isMint || isWipeOrBurn) {
            EntityId senderId = amount < 0 ? accountId : EntityId.EMPTY;
            EntityId receiverId = amount > 0 ? accountId : EntityId.EMPTY;
            syntheticContractLogService.create(
                    new TransferContractLog(recordItem, tokenId, senderId, receiverId, Math.abs(amount)));
        }
    }

    private void handleNegativeAccountAmounts(
            EntityId tokenId,
            TransactionBody body,
            AccountAmount accountAmount,
            long amount,
            TokenTransfer tokenTransfer) {
        // If a record AccountAmount with amount < 0 is not in the body;
        // but an AccountAmount with the same (TokenID, AccountID) combination is in the body with is_approval=true,
        // then again set is_approval=true
        if (amount < 0) {

            // Is the accountAmount from the record also inside a body's transfer list for the given tokenId?
            AccountAmount accountAmountInsideTransferList = findAccountAmount(accountAmount::equals, tokenId, body);
            if (accountAmountInsideTransferList == null) {

                // Is there any account amount inside the body's transfer list for the given tokenId
                // with the same accountId as the accountAmount from the record?
                AccountAmount accountAmountWithSameIdInsideBody = findAccountAmount(
                        aa -> aa.getAccountID().equals(accountAmount.getAccountID()) && aa.getIsApproval(),
                        tokenId,
                        body);
                if (accountAmountWithSameIdInsideBody != null) {
                    tokenTransfer.setIsApproval(true);
                }
            } else {
                tokenTransfer.setIsApproval(accountAmountInsideTransferList.getIsApproval());
            }
        }
    }

    private void insertTokenTransfers(RecordItem recordItem, Transaction transaction) {
        if (!entityProperties.getPersist().isTokens()) {
            return;
        }

        var payerAccountId = recordItem.getPayerAccountId();
        var tokenTransferListsList = recordItem.getTransactionRecord().getTokenTransferListsList();

        for (int i = 0; i < tokenTransferListsList.size(); i++) {
            TokenTransferList tokenTransferList = tokenTransferListsList.get(i);

            insertFungibleTokenTransfers(recordItem, tokenTransferList);
            insertNonFungibleTokenTransfers(recordItem, transaction, tokenTransferList);

            if (i == 0) {
                var tokenId = tokenTransferList.getToken();
                var entityTokenId = EntityId.of(tokenId);

                syntheticContractResultService.create(
                        new TransferContractResult(recordItem, entityTokenId, payerAccountId));
            }
        }

        if (!recordItem.getTransactionBody().hasCryptoTransfer()
                || !entityProperties.getPersist().isTrackAllowance()) {
            return;
        }

        var tokenTransfers = recordItem.getTransactionBody().getCryptoTransfer().getTokenTransfersList();
        long spenderId = payerAccountId.getId();
        if (!tokenTransfers.isEmpty() && recordItem.getTransactionRecord().hasContractCallResult()) {
            spenderId = EntityId.of(recordItem
                            .getTransactionRecord()
                            .getContractCallResult()
                            .getSenderId())
                    .getId();
        }
        long transferSpenderId = spenderId;
        tokenTransfers.forEach(tokenTransfer -> {
            var tokenId = EntityId.of(tokenTransfer.getToken());
            tokenTransfer.getTransfersList().forEach(accountAmount -> {
                // Emit allowance amount representing approved transfer debit
                if (accountAmount.getIsApproval() && accountAmount.getAmount() < 0) {
                    var tokenAllowance = TokenAllowance.builder()
                            .amount(accountAmount.getAmount())
                            .owner(EntityId.of(accountAmount.getAccountID()).getId())
                            .payerAccountId(payerAccountId)
                            .spender(transferSpenderId)
                            .tokenId(tokenId.getId())
                            .build();

                    entityListener.onTokenAllowance(tokenAllowance);
                }
            });
        });
    }

    private void insertNonFungibleTokenTransfers(
            RecordItem recordItem, Transaction transaction, TokenTransferList tokenTransferList) {
        if (tokenTransferList.getNftTransfersList().isEmpty()) {
            return;
        }

        var body = recordItem.getTransactionBody();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var tokenId = tokenTransferList.getToken();
        var entityTokenId = EntityId.of(tokenId);

        for (var nftTransfer : tokenTransferList.getNftTransfersList()) {
            long serialNumber = nftTransfer.getSerialNumber();
            var receiverId = EntityId.of(nftTransfer.getReceiverAccountID());
            var senderId = EntityId.of(nftTransfer.getSenderAccountID());

            var nftTransferDomain = new org.hiero.mirror.common.domain.token.NftTransfer();
            nftTransferDomain.setIsApproval(isApprovalNftTransfer(nftTransfer, tokenId, body));
            nftTransferDomain.setReceiverAccountId(receiverId);
            nftTransferDomain.setSenderAccountId(senderId);
            nftTransferDomain.setSerialNumber(serialNumber);
            nftTransferDomain.setTokenId(entityTokenId);
            transaction.addNftTransfer(nftTransferDomain);

            recordItem.addEntityId(receiverId);
            recordItem.addEntityId(senderId);
            recordItem.addEntityId(entityTokenId);

            transferNftOwnership(consensusTimestamp, serialNumber, entityTokenId, receiverId);
            syntheticContractLogService.create(
                    new TransferIndexedContractLog(recordItem, entityTokenId, senderId, receiverId, serialNumber));
        }
    }

    private void insertAutomaticTokenAssociations(RecordItem recordItem) {
        if (entityProperties.getPersist().isTokens()) {
            if (recordItem.getTransactionBody().hasTokenCreation()) {
                // Automatic token associations for token create transactions are handled by its transaction handler.
                return;
            }

            long consensusTimestamp = recordItem.getConsensusTimestamp();
            recordItem
                    .getTransactionRecord()
                    .getAutomaticTokenAssociationsList()
                    .forEach(tokenAssociation -> {
                        // The accounts and tokens in the associations should have been added to EntityListener when
                        // inserting the corresponding token transfers, so no need to duplicate the logic here
                        EntityId accountId = EntityId.of(tokenAssociation.getAccountId());
                        EntityId tokenId = EntityId.of(tokenAssociation.getTokenId());
                        TokenAccount tokenAccount = new TokenAccount();
                        tokenAccount.setAccountId(accountId.getId());
                        tokenAccount.setAssociated(true);
                        tokenAccount.setAutomaticAssociation(true);
                        tokenAccount.setBalance(0L);
                        tokenAccount.setBalanceTimestamp(consensusTimestamp);
                        tokenAccount.setCreatedTimestamp(consensusTimestamp);
                        tokenAccount.setTimestampRange(Range.atLeast(consensusTimestamp));
                        tokenAccount.setTokenId(tokenId.getId());
                        entityListener.onTokenAccount(tokenAccount);
                        recordItem.addEntityId(accountId);
                        recordItem.addEntityId(tokenId);
                    });
        }
    }

    private void transferNftOwnership(
            long consensusTimeStamp, long serialNumber, EntityId tokenId, EntityId receiverId) {
        if (EntityId.isEmpty(receiverId) || serialNumber == WILDCARD_SERIAL_NUMBER) {
            // nfts in token burn / wipe transactions are handled in transaction handlers, also skip wildcard nft
            return;
        }

        var nft = Nft.builder()
                .accountId(receiverId)
                .serialNumber(serialNumber)
                .timestampRange(Range.atLeast(consensusTimeStamp))
                .tokenId(tokenId.getId())
                .build();
        entityListener.onNft(nft);
    }

    @SuppressWarnings({"deprecation", "java:S135"})
    private void insertTransactionSignatures(
            EntityId entityId, long consensusTimestamp, List<SignaturePair> signaturePairList) {
        Set<ByteString> publicKeyPrefixes = new HashSet<>();
        for (SignaturePair signaturePair : signaturePairList) {
            ByteString prefix = signaturePair.getPubKeyPrefix();
            ByteString signature = null;
            var signatureCase = signaturePair.getSignatureCase();
            int type = signatureCase.getNumber();

            switch (signatureCase) {
                case CONTRACT:
                    signature = signaturePair.getContract();
                    break;
                case ECDSA_384:
                    signature = signaturePair.getECDSA384();
                    break;
                case ECDSA_SECP256K1:
                    signature = signaturePair.getECDSASecp256K1();
                    break;
                case ED25519:
                    signature = signaturePair.getEd25519();
                    break;
                case RSA_3072:
                    signature = signaturePair.getRSA3072();
                    break;
                case SIGNATURE_NOT_SET:
                    Map<Integer, UnknownFieldSet.Field> unknownFields =
                            signaturePair.getUnknownFields().asMap();

                    // If we encounter a signature that our version of the protobuf does not yet support, it will
                    // return SIGNATURE_NOT_SET. Hence we should look in the unknown fields for the new signature.
                    // ByteStrings are stored as length-delimited on the wire, so we search the unknown fields for a
                    // field that has exactly one length-delimited value and assume it's our new signature bytes.
                    for (Map.Entry<Integer, UnknownFieldSet.Field> entry : unknownFields.entrySet()) {
                        UnknownFieldSet.Field field = entry.getValue();
                        if (field.getLengthDelimitedList().size() == 1) {
                            signature = field.getLengthDelimitedList().get(0);
                            type = entry.getKey();
                            break;
                        }
                    }

                    if (signature == null) {
                        Utility.handleRecoverableError(
                                "Unsupported signature at {}: {}", consensusTimestamp, unknownFields);
                        continue;
                    }
                    break;
                default:
                    Utility.handleRecoverableError(
                            "Unsupported signature case at {}: {}",
                            consensusTimestamp,
                            signaturePair.getSignatureCase());
                    continue;
            }

            // Handle potential public key prefix collisions by taking first occurrence only ignoring duplicates
            if (publicKeyPrefixes.add(prefix)) {
                TransactionSignature transactionSignature = new TransactionSignature();
                transactionSignature.setConsensusTimestamp(consensusTimestamp);
                transactionSignature.setEntityId(entityId);
                transactionSignature.setPublicKeyPrefix(DomainUtils.toBytes(prefix));
                transactionSignature.setSignature(DomainUtils.toBytes(signature));
                transactionSignature.setType(type);
                entityListener.onTransactionSignature(transactionSignature);
            }
        }
    }

    private void onScheduledTransaction(RecordItem recordItem) {
        if (entityProperties.getPersist().isSchedules()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            var transactionRecord = recordItem.getTransactionRecord();

            // update schedule execute time
            var schedule = new Schedule();
            var scheduleId = EntityId.of(transactionRecord.getScheduleRef());
            schedule.setScheduleId(scheduleId);
            schedule.setExecutedTimestamp(consensusTimestamp);
            entityListener.onSchedule(schedule);
            recordItem.addEntityId(scheduleId);
        }
    }

    private void insertAssessedCustomFees(RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        for (var protoAssessedCustomFee : recordItem.getTransactionRecord().getAssessedCustomFeesList()) {
            var collectorAccountId = EntityId.of(protoAssessedCustomFee.getFeeCollectorAccountId());
            // the effective payers must also appear in the *transfer lists of this transaction and the
            // corresponding EntityIds should have been added to EntityListener, so skip it here.
            var tokenId = EntityId.of(protoAssessedCustomFee.getTokenId());
            var assessedCustomFee = new AssessedCustomFee();
            assessedCustomFee.setAmount(protoAssessedCustomFee.getAmount());
            assessedCustomFee.setCollectorAccountId(collectorAccountId.getId());
            assessedCustomFee.setConsensusTimestamp(consensusTimestamp);
            assessedCustomFee.setPayerAccountId(recordItem.getPayerAccountId());
            assessedCustomFee.setTokenId(tokenId);

            if (protoAssessedCustomFee.getEffectivePayerAccountIdCount() > 0) {
                var effectivePayerEntityIds = new ArrayList<Long>();
                for (var protoAccountId : protoAssessedCustomFee.getEffectivePayerAccountIdList()) {
                    var effectivePayerAccountId = EntityId.of(protoAccountId);
                    effectivePayerEntityIds.add(effectivePayerAccountId.getId());
                    recordItem.addEntityId(effectivePayerAccountId);
                }
                assessedCustomFee.setEffectivePayerAccountIds(effectivePayerEntityIds);
            }

            entityListener.onAssessedCustomFee(assessedCustomFee);

            recordItem.addEntityId(collectorAccountId);
            recordItem.addEntityId(tokenId);
        }
    }

    // regardless of transaction type, filter on entityId and payer account and transfer tokens/receivers/senders
    private TransactionFilterFields getTransactionFilterFields(EntityId entityId, RecordItem recordItem) {
        if (!commonParserProperties.hasFilter()) {
            return TransactionFilterFields.EMPTY;
        }

        var entities = new HashSet<EntityId>();
        entities.add(entityId);
        entities.add(recordItem.getPayerAccountId());

        recordItem
                .getTransactionRecord()
                .getTransferList()
                .getAccountAmountsList()
                .forEach(accountAmount -> entities.add(EntityId.of(accountAmount.getAccountID())));

        recordItem.getTransactionRecord().getTokenTransferListsList().forEach(transfer -> {
            entities.add(EntityId.of(transfer.getToken()));

            transfer.getTransfersList()
                    .forEach(accountAmount -> entities.add(EntityId.of(accountAmount.getAccountID())));

            transfer.getNftTransfersList().forEach(nftTransfer -> {
                entities.add(EntityId.of(nftTransfer.getReceiverAccountID()));
                entities.add(EntityId.of(nftTransfer.getSenderAccountID()));
            });
        });

        entities.remove(null);
        return new TransactionFilterFields(entities, recordItem);
    }
}
