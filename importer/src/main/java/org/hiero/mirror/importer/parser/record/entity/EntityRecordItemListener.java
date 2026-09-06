// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.hiero.mirror.common.domain.token.NftTransfer.WILDCARD_SERIAL_NUMBER;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.hiero.mirror.importer.parser.record.RecordParserProperties;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import org.hiero.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import org.hiero.mirror.importer.util.Utility;

@CustomLog
@Named
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
    private final RecordParserProperties parserProperties;

    @Override
    public void onItem(final RecordItem recordItem) throws ImporterException {
        if (!parserProperties.isEnabled()) {
            return;
        }

        final var persistProperties = entityProperties.getPersist();
        recordItem.setEntityTransactionPredicate(persistProperties::shouldPersistEntityTransaction);
        recordItem.setEntityNftTransactionPredicate(persistProperties::shouldPersistEntityNftTransaction);
        recordItem.setContractTransactionPredicate(_ -> persistProperties.isContractTransaction());

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

        // The body can't be trusted for a non-successful transaction, so skip alias resolution entirely rather
        // than let an attacker pad a failing transaction with unresolvable aliases to burn mirror node cycles.
        final var approvedDebits =
                recordItem.isSuccessful() ? approvedDebits(recordItem.getTransactionBody()) : Set.<ApprovalKey>of();

        // Insert transfers even on failure
        insertTransferList(recordItem, approvedDebits);
        insertStakingRewardTransfers(recordItem);

        // handle scheduled transaction, even on failure
        if (transaction.isScheduled()) {
            onScheduledTransaction(recordItem);
        }

        if (recordItem.isSuccessful()) {
            if (persistProperties.getTransactionSignatures().contains(transactionType)) {
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
            insertTokenTransfers(recordItem, transaction, approvedDebits);
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

    private Transaction buildTransaction(final EntityId entityId, final RecordItem recordItem) {
        final var body = recordItem.getTransactionBody();
        final var txRecord = recordItem.getTransactionRecord();

        final var validDurationSeconds = body.hasTransactionValidDuration()
                ? body.getTransactionValidDuration().getSeconds()
                : null;
        final var nodeAccount = EntityId.tryOf(body.getNodeAccountID());
        final var transactionId = body.getTransactionID();

        // build transaction
        final var transaction = new Transaction();
        transaction.setChargedTxFee(txRecord.getTransactionFee());
        transaction.setCongestionPricingMultiplier(recordItem.getCongestionPricingMultiplier());
        transaction.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        transaction.setEntityId(entityId);
        transaction.setHighVolume(body.getHighVolume());
        transaction.setHighVolumePricingMultiplier(txRecord.getHighVolumePricingMultiplier());
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

        long spenderId = transfers.isEmpty() ? payerAccount.getId() : getAllowanceSpenderId(recordItem, payerAccount);

        for (var aa : transfers) {
            var entityId = resolve(aa.getAccountID());
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
                        .spender(spenderId)
                        .build();
                entityListener.onCryptoAllowance(cryptoAllowance);
            }
        }
    }

    /**
     * Resolves the spender of an approved transfer. For a transfer initiated by a contract, either directly or from
     * a contract create's constructor, the spender is identified by the contract function result's sender id;
     * otherwise it's the transaction payer.
     */
    private long getAllowanceSpenderId(RecordItem recordItem, EntityId payerAccountId) {
        var transactionRecord = recordItem.getTransactionRecord();
        var contractFunctionResult = transactionRecord.hasContractCreateResult()
                ? transactionRecord.getContractCreateResult()
                : transactionRecord.getContractCallResult();
        return contractFunctionResult.hasSenderId()
                ? EntityId.of(contractFunctionResult.getSenderId()).getId()
                : payerAccountId.getId();
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
    private void insertTransferList(RecordItem recordItem, Set<ApprovalKey> approvedDebits) {
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
            final var aa = transferList.getAccountAmounts(i);
            final var account = EntityId.of(aa.getAccountID());
            final var cryptoTransfer = new CryptoTransfer();
            cryptoTransfer.setAmount(aa.getAmount());
            cryptoTransfer.setConsensusTimestamp(consensusTimestamp);
            cryptoTransfer.setEntityId(account.getId());
            cryptoTransfer.setPayerAccountId(payerAccountId);
            cryptoTransfer.setIsApproval(approvedDebits.contains(new ApprovalKey(account, EntityId.EMPTY, 0L)));

            if (failedTransfer) {
                final var accountAmountInsideBody = findAccountAmount(aa, body);
                if (accountAmountInsideBody != null) {
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

    private record ApprovalKey(EntityId account, EntityId token, long serial) {}

    private Set<ApprovalKey> approvedDebits(TransactionBody body) {
        if (!body.hasCryptoTransfer()) {
            return Set.of();
        }

        final var cryptoTransfer = body.getCryptoTransfer();
        final var approvals = new HashSet<ApprovalKey>();

        for (final var accountAmount : cryptoTransfer.getTransfers().getAccountAmountsList()) {
            if (accountAmount.getIsApproval() && accountAmount.getAmount() < 0) {
                approvals.add(new ApprovalKey(resolve(accountAmount.getAccountID()), EntityId.EMPTY, 0L));
            }
        }

        for (final var tokenTransfers : cryptoTransfer.getTokenTransfersList()) {
            final var token = EntityId.of(tokenTransfers.getToken());

            for (final var accountAmount : tokenTransfers.getTransfersList()) {
                if (accountAmount.getIsApproval() && accountAmount.getAmount() < 0) {
                    approvals.add(new ApprovalKey(resolve(accountAmount.getAccountID()), token, 0L));
                }
            }

            for (final var nftTransfer : tokenTransfers.getNftTransfersList()) {
                if (nftTransfer.getIsApproval()) {
                    approvals.add(new ApprovalKey(
                            resolve(nftTransfer.getSenderAccountID()), token, nftTransfer.getSerialNumber()));
                }
            }
        }

        return approvals;
    }

    private EntityId resolve(AccountID accountId) {
        return entityIdService.lookup(accountId).orElse(EntityId.EMPTY);
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
            recordItem.addEntityId(EntityId.tryOf(maxCustomFee.getAccountId()));
            for (var fixedFee : maxCustomFee.getFeesList()) {
                recordItem.addEntityId(EntityId.tryOf(fixedFee.getDenominatingTokenId()));
            }
        }

        return maxCustomFees;
    }

    private void insertFungibleTokenTransfers(
            RecordItem recordItem, TokenTransferList tokenTransferList, Set<ApprovalKey> approvedDebits) {
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

        for (AccountAmount accountAmount : tokenTransfers) {
            EntityId accountId = EntityId.of(accountAmount.getAccountID());
            long amount = accountAmount.getAmount();
            var tokenTransfer = isDeletedTokenDissociate ? new DissociateTokenTransfer() : new TokenTransfer();
            tokenTransfer.setAmount(amount);
            tokenTransfer.setId(new TokenTransfer.Id(consensusTimestamp, tokenId, accountId));
            tokenTransfer.setIsApproval(false);
            tokenTransfer.setPayerAccountId(payerAccountId);

            if (amount < 0) {
                tokenTransfer.setIsApproval(approvedDebits.contains(new ApprovalKey(accountId, tokenId, 0L)));
            }
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

        transferEventsGenerator.generate(recordItem, tokenId, tokenTransfers);
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

    private void insertTokenTransfers(RecordItem recordItem, Transaction transaction, Set<ApprovalKey> approvedDebits) {
        if (!entityProperties.getPersist().isTokens()) {
            return;
        }

        var payerAccountId = recordItem.getPayerAccountId();
        var tokenTransferListsList = recordItem.getTransactionRecord().getTokenTransferListsList();

        for (int i = 0; i < tokenTransferListsList.size(); i++) {
            TokenTransferList tokenTransferList = tokenTransferListsList.get(i);

            insertFungibleTokenTransfers(recordItem, tokenTransferList, approvedDebits);
            insertNonFungibleTokenTransfers(recordItem, transaction, tokenTransferList, approvedDebits);

            if (i == 0) {
                var tokenId = tokenTransferList.getToken();
                var entityTokenId = EntityId.of(tokenId);

                syntheticContractResultService.create(
                        new TransferContractResult(recordItem, entityTokenId, payerAccountId));
            }
        }

        if (!recordItem.isSuccessful()
                || !recordItem.getTransactionBody().hasCryptoTransfer()
                || !entityProperties.getPersist().isTrackAllowance()) {
            return;
        }

        var tokenTransfers = recordItem.getTransactionBody().getCryptoTransfer().getTokenTransfersList();
        long transferSpenderId =
                tokenTransfers.isEmpty() ? payerAccountId.getId() : getAllowanceSpenderId(recordItem, payerAccountId);
        tokenTransfers.forEach(tokenTransfer -> {
            var tokenId = EntityId.tryOf(tokenTransfer.getToken());
            tokenTransfer.getTransfersList().forEach(accountAmount -> {
                // Emit allowance amount representing approved transfer debit
                if (accountAmount.getIsApproval() && accountAmount.getAmount() < 0) {
                    var owner = resolve(accountAmount.getAccountID());
                    if (EntityId.isEmpty(owner)) {
                        return;
                    }

                    var tokenAllowance = TokenAllowance.builder()
                            .amount(accountAmount.getAmount())
                            .owner(owner.getId())
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
            RecordItem recordItem,
            Transaction transaction,
            TokenTransferList tokenTransferList,
            Set<ApprovalKey> approvedDebits) {
        if (tokenTransferList.getNftTransfersList().isEmpty()) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var tokenId = tokenTransferList.getToken();
        var entityTokenId = EntityId.of(tokenId);

        for (var nftTransfer : tokenTransferList.getNftTransfersList()) {
            long serialNumber = nftTransfer.getSerialNumber();
            var receiverId = EntityId.of(nftTransfer.getReceiverAccountID());
            var senderId = EntityId.of(nftTransfer.getSenderAccountID());

            var nftTransferDomain = new org.hiero.mirror.common.domain.token.NftTransfer();
            nftTransferDomain.setIsApproval(
                    approvedDebits.contains(new ApprovalKey(senderId, entityTokenId, serialNumber)));
            nftTransferDomain.setReceiverAccountId(receiverId);
            nftTransferDomain.setSenderAccountId(senderId);
            nftTransferDomain.setSerialNumber(serialNumber);
            nftTransferDomain.setTokenId(entityTokenId);
            transaction.addNftTransfer(nftTransferDomain);

            recordItem.addEntityId(receiverId);
            recordItem.addEntityId(senderId);
            recordItem.addEntityId(entityTokenId);

            recordItem.addNftTransactionEntityId(receiverId);
            recordItem.addNftTransactionEntityId(senderId);

            transferNftOwnership(consensusTimestamp, serialNumber, entityTokenId, receiverId);
            // If there is a wildcard used as a serial number for an NFT transfer, the importer won't create
            // synthetic logs for each serial number from the NFT collection due to performance considerations.
            // This behaviour will be improved in a future task.
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
