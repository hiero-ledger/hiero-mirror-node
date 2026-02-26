// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.sql;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.FungibleAllowance;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookStorage;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.token.AbstractNft;
import org.hiero.mirror.common.domain.token.AbstractTokenAccount.Id;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.NftTransfer;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.common.domain.topic.Topic;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.hiero.mirror.common.domain.transaction.CryptoTransfer;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.LiveHash;
import org.hiero.mirror.common.domain.transaction.NetworkFreeze;
import org.hiero.mirror.common.domain.transaction.Prng;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.StakingRewardTransfer;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.exception.ImporterException;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.batch.BatchPersister;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.hiero.mirror.importer.repository.NftRepository;
import org.hiero.mirror.importer.repository.TokenAccountRepository;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.core.annotation.Order;
import org.springframework.util.CollectionUtils;

@CustomLog
@Named
@Order(3)
@ConditionOnEntityRecordParser
@RequiredArgsConstructor
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {

    private static final List<Class<?>> NFT_FLUSH = List.of(Token.class, TokenAccount.class, Nft.class);

    private final BatchPersister batchPersister;
    private final ParserContext context;
    private final EntityIdService entityIdService;
    private final EntityProperties entityProperties;
    private final NftRepository nftRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final SqlProperties sqlProperties;

    @Override
    public boolean isEnabled() {
        return sqlProperties.isEnabled();
    }

    @Override
    public void onEnd(RecordFile recordFile) {
        flush();
    }

    @Override
    public void onAssessedCustomFee(AssessedCustomFee assessedCustomFee) throws ImporterException {
        context.add(assessedCustomFee);
    }

    @Override
    public void onContract(Contract contract) {
        context.add(contract);
    }

    @Override
    public void onContractAction(ContractAction contractAction) {
        context.add(contractAction);
    }

    @Override
    public void onContractLog(ContractLog contractLog) {
        context.add(contractLog);
    }

    @Override
    public void onContractResult(ContractResult contractResult) throws ImporterException {
        context.add(contractResult);
        if (entityProperties.getPersist().isContractTransactionHash()) {
            context.add(contractResult.toContractTransactionHash());
        }
    }

    @Override
    public void onContractStateChange(ContractStateChange contractStateChange) {
        context.add(contractStateChange);

        var valueRead = contractStateChange.getValueRead();
        var valueWritten = contractStateChange.getValueWritten();
        if (valueWritten != null || contractStateChange.isMigration()) {
            var value = valueWritten == null ? valueRead : valueWritten;
            var state = new ContractState();
            state.setContractId(contractStateChange.getContractId());
            state.setCreatedTimestamp(contractStateChange.getConsensusTimestamp());
            state.setModifiedTimestamp(contractStateChange.getConsensusTimestamp());
            state.setSlot(contractStateChange.getSlot());
            state.setValue(value);
            context.merge(state.getId(), state, this::mergeContractState);
        }
    }

    @Override
    public void onContractTransactions(Collection<ContractTransaction> contractTransactions) {
        if (entityProperties.getPersist().isContractTransaction()) {
            context.addAll(contractTransactions);
        }
    }

    @Override
    public void onCryptoAllowance(CryptoAllowance cryptoAllowance) {
        context.merge(cryptoAllowance.getId(), cryptoAllowance, this::mergeFungibleAllowance);
    }

    @Override
    public void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
        if (entityProperties.getPersist().isTrackBalance()) {
            var entity = new Entity();
            entity.setId(cryptoTransfer.getEntityId());
            entity.setBalance(cryptoTransfer.getAmount());
            entity.setBalanceTimestamp(cryptoTransfer.getConsensusTimestamp());
            onEntity(entity);
        }

        context.add(cryptoTransfer);
    }

    @Override
    public void onCustomFee(CustomFee customFee) throws ImporterException {
        context.merge(customFee.getEntityId(), customFee, this::mergeCustomFee);
    }

    @Override
    public void onEntity(Entity entity) throws ImporterException {
        long id = entity.getId();
        if (id == EntityId.EMPTY.getId()) {
            return;
        }

        if (entity.hasHistory()
                && entity.getCreatedTimestamp() == null
                && !entityProperties.getPersist().isEntityHistory()) {
            return;
        }

        context.merge(id, entity, this::mergeEntity);
        entityIdService.notify(entity);
    }

    @Override
    public void onEntityTransactions(Collection<EntityTransaction> entityTransactions) throws ImporterException {
        context.addAll(entityTransactions);
    }

    @Override
    public void onEthereumTransaction(EthereumTransaction ethereumTransaction) throws ImporterException {
        context.add(ethereumTransaction);

        if (entityProperties.getPersist().shouldPersistTransactionHash(TransactionType.ETHEREUMTRANSACTION)
                && ArrayUtils.isNotEmpty(ethereumTransaction.getHash())) {
            context.add(ethereumTransaction.toTransactionHash());
        }
    }

    @Override
    public void onFileData(FileData fileData) {
        context.add(fileData);
    }

    @Override
    public void onHook(Hook hook) {
        context.merge(hook.getId(), hook, this::mergeHook);
    }

    @Override
    public void onHookStorageChange(HookStorageChange storageChange) throws ImporterException {
        context.add(storageChange);

        if (storageChange.getValueWritten() != null) {
            final var hookStorage = HookStorage.builder()
                    .createdTimestamp(storageChange.getConsensusTimestamp())
                    .hookId(storageChange.getHookId())
                    .ownerId(storageChange.getOwnerId())
                    .key(storageChange.getKey())
                    .modifiedTimestamp(storageChange.getConsensusTimestamp())
                    .value(storageChange.getValueWritten())
                    .build();

            context.merge(hookStorage.getId(), hookStorage, this::mergeHookStorage);
        }
    }

    @Override
    public void onLedger(final Ledger ledger) throws ImporterException {
        context.merge(ledger.getLedgerId(), ledger, this::mergeLedger);
    }

    @Override
    public void onLiveHash(LiveHash liveHash) throws ImporterException {
        context.add(liveHash);
    }

    @Override
    public void onNetworkFreeze(NetworkFreeze networkFreeze) {
        context.add(networkFreeze);
    }

    @Override
    public void onNetworkStake(NetworkStake networkStake) throws ImporterException {
        context.add(networkStake);
    }

    @Override
    public void onNft(Nft nft) throws ImporterException {
        context.merge(nft.getId(), nft, this::mergeNft);
    }

    @Override
    public void onNftAllowance(NftAllowance nftAllowance) {
        context.merge(nftAllowance.getId(), nftAllowance, this::mergeNftAllowance);
    }

    @Override
    public void onNode(Node node) {
        context.merge(node.getNodeId(), node, this::mergeNode);
    }

    @Override
    public void onNodeStake(NodeStake nodeStake) {
        context.add(nodeStake);
    }

    @Override
    public void onPrng(Prng prng) {
        context.add(prng);
    }

    @Override
    public void onRegisteredNode(RegisteredNode registeredNode) {
        context.merge(registeredNode.getRegisteredNodeId(), registeredNode, this::mergeRegisteredNode);
    }

    @Override
    public void onSchedule(Schedule schedule) throws ImporterException {
        context.merge(schedule.getScheduleId(), schedule, this::mergeSchedule);
    }

    @Override
    public void onStakingRewardTransfer(StakingRewardTransfer stakingRewardTransfer) {
        context.add(stakingRewardTransfer);

        var current = context.get(Entity.class, stakingRewardTransfer.getAccountId());
        long consensusTimestamp = stakingRewardTransfer.getConsensusTimestamp();
        // The new stake period start is set to today - 1, so that when today ends, the account / contract will earn
        // staking reward for today
        long stakePeriodStart = Utility.getEpochDay(consensusTimestamp) - 1;
        if (current == null
                || current.getStakePeriodStart() == null
                || current.getStakePeriodStart() < stakePeriodStart) {
            // Set the stake period start when any of the following is true
            // 1. The entity is not in the state
            // 2. The current stake period start is not set
            // 3. The current stake period start is before the new stake period start as result of the reward payout
            // Note condition 3 handles the edge case that the staking reward transfer is triggered by an entity update
            // transaction with entity staking changes so the stake period start should be today, not today - 1
            var entity = EntityId.of(stakingRewardTransfer.getAccountId()).toEntity();
            entity.setStakePeriodStart(stakePeriodStart);
            entity.setTimestampLower(consensusTimestamp);
            entity.setType(null); // Clear the type since it's uncertain if the entity is ACCOUNT or CONTRACT
            onEntity(entity);
        }
    }

    @Override
    public void onToken(Token token) throws ImporterException {
        context.merge(token.getTokenId(), token, this::mergeToken);
    }

    @Override
    public void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {
        var id = tokenAccount.getId();

        // Users might have already manually associated to this token before claiming the airdrop
        if (tokenAccount.isClaim() && isTokenAccountAlreadyAssociated(id)) {
            return;
        }

        context.merge(id, tokenAccount, this::mergeTokenAccount);
    }

    private boolean isTokenAccountAlreadyAssociated(Id id) {
        var existing = context.get(TokenAccount.class, id);

        if (existing != null) {
            return Objects.requireNonNullElse(existing.getAssociated(), true);
        }

        return tokenAccountRepository
                .findById(id)
                .map(TokenAccount::getAssociated)
                .orElse(false);
    }

    @Override
    public void onTokenAirdrop(TokenAirdrop tokenAirdrop) {
        if (entityProperties.getPersist().isTokenAirdrops()) {
            context.merge(tokenAirdrop.getId(), tokenAirdrop, this::mergeTokenAirdrop);
        }
    }

    @Override
    public void onTokenAllowance(TokenAllowance tokenAllowance) {
        context.merge(tokenAllowance.getId(), tokenAllowance, this::mergeFungibleAllowance);
    }

    @Override
    public void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {
        if (entityProperties.getPersist().isTrackBalance()) {
            var tokenAccount = new TokenAccount();
            var tokenTransferId = Objects.requireNonNull(tokenTransfer.getId());
            tokenAccount.setAccountId(tokenTransferId.getAccountId().getId());
            tokenAccount.setAssociated(true);
            tokenAccount.setTokenId(tokenTransferId.getTokenId().getId());
            tokenAccount.setBalance(tokenTransfer.getAmount());
            tokenAccount.setBalanceTimestamp(tokenTransferId.getConsensusTimestamp());
            onTokenAccount(tokenAccount);
        }

        context.add(tokenTransfer);
    }

    @Override
    public void onTopic(Topic topic) throws ImporterException {
        context.merge(topic.getId(), topic, this::mergeTopic);
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        context.add(topicMessage);
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        var key = TransactionType.ATOMIC_BATCH.getProtoId() == transaction.getType()
                ? transaction.getConsensusTimestamp()
                : null;
        context.add(transaction, key);

        if (transaction.getBatchKey() != null && transaction.getNonce() == 0) {
            Transaction batchParent = context.get(Transaction.class, transaction.getParentConsensusTimestamp());

            if (batchParent == null) {
                Utility.handleRecoverableError(
                        "Batch parent not found for transaction: " + transaction.getConsensusTimestamp());
            } else {
                batchParent.addInnerTransaction(transaction);
            }
        }

        if (entityProperties.getPersist().shouldPersistTransactionHash(TransactionType.of(transaction.getType()))) {
            var hash = transaction.toTransactionHash();
            if (hash != null && hash.hashIsValid()) {
                context.add(hash);
            }
        }

        onNftTransferList(transaction);
    }

    @Override
    public void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {
        context.add(transactionSignature);
    }

    private void flush() {
        try {
            var stopwatch = Stopwatch.createStarted();
            context.forEach(batchPersister::persist);
            log.info("Completed batch inserts in {}", stopwatch);
        } catch (ParserException e) {
            throw e;
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

    private void flushNftState() {
        try {
            // flush tables required for an accurate nft state in database to ensure correct state-dependent changes
            NFT_FLUSH.forEach(c -> {
                var data = context.get(c);
                batchPersister.persist(data);
                context.remove(c);
            });
        } catch (ParserException e) {
            throw e;
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

    private CustomFee mergeCustomFee(CustomFee previous, CustomFee current) {
        previous.setTimestampUpper(current.getTimestampLower());
        return current;
    }

    private ContractState mergeContractState(ContractState previous, ContractState current) {
        previous.setValue(current.getValue());
        previous.setModifiedTimestamp(current.getModifiedTimestamp());
        return previous;
    }

    @SuppressWarnings("java:S3776")
    private Entity mergeEntity(Entity previous, Entity current) {
        // This entity should not trigger a history record, so just copy common non-history fields, if set, to previous
        if (!current.hasHistory()) {
            previous.addBalance(current.getBalance());
            if (current.getBalanceTimestamp() != null) {
                previous.setBalanceTimestamp(current.getBalanceTimestamp());
            }

            if (current.getEthereumNonce() != null) {
                previous.setEthereumNonce(current.getEthereumNonce());
            }

            if (current.getStakePeriodStart() != null) {
                previous.setStakePeriodStart(current.getStakePeriodStart());
            }

            return previous;
        }

        // If previous doesn't have history, merge reversely from current to previous
        var src = previous.hasHistory() ? previous : current;
        var dest = previous.hasHistory() ? current : previous;

        boolean isSameTimestampLower = Objects.equals(current.getTimestampLower(), previous.getTimestampLower());
        if (current.hasHistory() && isSameTimestampLower) {
            // Copy from current to previous if the updates have the same lower timestamp (thus from same transaction)
            src = current;
            dest = previous;
        }

        dest.setCreatedTimestamp(src.getCreatedTimestamp());

        if (dest.getAlias() == null) {
            dest.setAlias(src.getAlias());
        }

        if (dest.getAutoRenewPeriod() == null) {
            dest.setAutoRenewPeriod(src.getAutoRenewPeriod());
        }

        if (dest.getAutoRenewAccountId() == null) {
            dest.setAutoRenewAccountId(src.getAutoRenewAccountId());
        }

        dest.addBalance(src.getBalance());
        if (dest.getBalanceTimestamp() == null) {
            dest.setBalanceTimestamp(src.getBalanceTimestamp());
        }

        if (dest.getDeclineReward() == null) {
            dest.setDeclineReward(src.getDeclineReward());
        }

        if (dest.getDeleted() == null) {
            dest.setDeleted(src.getDeleted());
        }

        if (dest.getDelegationAddress() == null) {
            dest.setDelegationAddress(src.getDelegationAddress());
        }

        if (dest.getEthereumNonce() == null) {
            dest.setEthereumNonce(src.getEthereumNonce());
        }

        if (dest.getEvmAddress() == null) {
            dest.setEvmAddress(src.getEvmAddress());
        }

        if (dest.getExpirationTimestamp() == null) {
            dest.setExpirationTimestamp(src.getExpirationTimestamp());
        }

        if (dest.getKey() == null) {
            dest.setKey(src.getKey());
        }

        if (dest.getMaxAutomaticTokenAssociations() == null) {
            dest.setMaxAutomaticTokenAssociations(src.getMaxAutomaticTokenAssociations());
        }

        if (dest.getMemo() == null) {
            dest.setMemo(src.getMemo());
        }

        if (dest.getNum() == null) {
            dest.setNum(src.getNum());
        }

        if (dest.getObtainerId() == null) {
            dest.setObtainerId(src.getObtainerId());
        }

        if (dest.getPermanentRemoval() == null) {
            dest.setPermanentRemoval(src.getPermanentRemoval());
        }

        if (dest.getProxyAccountId() == null) {
            dest.setProxyAccountId(src.getProxyAccountId());
        }

        if (dest.getReceiverSigRequired() == null) {
            dest.setReceiverSigRequired(src.getReceiverSigRequired());
        }

        if (dest.getRealm() == null) {
            dest.setRealm(src.getRealm());
        }

        if (dest.getShard() == null) {
            dest.setShard(src.getShard());
        }

        if (dest.getStakedAccountId() == null) {
            dest.setStakedAccountId(src.getStakedAccountId());
        }

        if (dest.getStakedNodeId() == null) {
            dest.setStakedNodeId(src.getStakedNodeId());
        }

        if (dest.getStakePeriodStart() == null) {
            dest.setStakePeriodStart(src.getStakePeriodStart());
        }

        if (dest.getType() == null) {
            dest.setType(src.getType());
        }

        // There is at least one entity with history. If there is one without history, it must be dest and copy non-null
        // fields and timestamp range from src to dest. Otherwise, both have history, and it's a normal merge from
        // previous to current, so close the src entity's timestamp range
        if (!dest.hasHistory()) {
            dest.setTimestampRange(src.getTimestampRange());
            // It's important to set the type since some non-history updates may have incorrect entity type.
            // For example, when a contract is created in a child transaction, the initial transfer to the contract may
            // be externalized in the parent transaction record with an earlier consensus timestamp, so the non-history
            // entity is created from the crypto transfer then an entity with correct type is created from the contract
            // create child transaction
            dest.setType(src.getType());
        } else if (!isSameTimestampLower) {
            src.setTimestampUpper(dest.getTimestampLower());
        }

        return dest;
    }

    private <T extends FungibleAllowance> T mergeFungibleAllowance(T previous, T current) {
        if (current.hasHistory()) {
            // Current is an allowance grant / revoke so close the previous timestamp range
            previous.setTimestampUpper(current.getTimestampLower());
            return current;
        }

        // Current must be an approved transfer and previous can be either so should accumulate the amounts regardless.
        previous.setAmount(previous.getAmount() + current.getAmount());
        return previous;
    }

    private Ledger mergeLedger(final Ledger previous, final Ledger current) {
        // always override, i.e., copy info from current to previous
        previous.setConsensusTimestamp(current.getConsensusTimestamp());
        previous.setHistoryProofVerificationKey(current.getHistoryProofVerificationKey());
        previous.setNodeContributions(current.getNodeContributions());

        return previous;
    }

    private Nft mergeNft(Nft cachedNft, Nft newNft) {
        var dest = newNft;
        var src = cachedNft;
        if (Objects.equals(cachedNft.getTimestampLower(), newNft.getTimestampLower())) {
            // Two NFT updates for the same nft can have the same lower timestamp in 3 cases
            // - token mint, the NFT object built in the transaction handler has everything but accountId, the NFT
            //   object built in EntityRecordItemListener has accountId for ownership transfer
            // - duplicate NFT objects due to possible duplicate nft transfers in record
            // - same NFT with multiple transfers in record
            // We should merge updates from newNft to cachedNft instead
            if (newNft.getAccountId() != null) {
                // If the same nft is transferred from Alice to Bob, and from Bob to Carol at the same time, set the
                // owner account to Carol
                cachedNft.setAccountId(newNft.getAccountId());
            }

            dest = cachedNft;
            src = newNft;
        }

        // Never merge delegatingSpender and spender since any change to the same nft afterward should clear them
        if (dest.getAccountId() == null) {
            dest.setAccountId(src.getAccountId());
        }

        if (dest.getCreatedTimestamp() == null) {
            dest.setCreatedTimestamp(src.getCreatedTimestamp());
        }

        if (dest.getDeleted() == null) {
            dest.setDeleted(src.getDeleted());
        }

        if (dest.getMetadata() == null) {
            dest.setMetadata(src.getMetadata());
        }

        if (dest.getTimestampLower() > src.getTimestampLower()) {
            // Only close the source NFT timestamp range when the dest timestamp is after the src
            src.setTimestampUpper(dest.getTimestampLower());
        }

        /*
         * An unset spender field indicates this is an NFT metadata only update, and that the existing allowance
         * information must be retained from the source.
         */
        if (AbstractNft.shouldKeepSpender(dest.getSpender())) {
            dest.setSpender(src.getSpender());
        }

        if (AbstractNft.shouldKeepSpender(dest.getDelegatingSpender())) {
            dest.setDelegatingSpender(src.getDelegatingSpender());
        }

        return dest;
    }

    private Hook mergeHook(Hook previous, Hook current) {
        long adjustment =
                previous.getDeleted() && previous.getTimestampLower().equals(current.getTimestampLower()) ? 1 : 0;
        previous.setTimestampUpper(current.getTimestampLower() + adjustment);

        if (current.getCreatedTimestamp() != null
                && current.getCreatedTimestamp().equals(current.getTimestampLower())) {
            // hook creation, don't merge
            return current;
        }

        if (current.getExtensionPoint() == null) {
            current.setExtensionPoint(previous.getExtensionPoint());
        }
        if (current.getType() == null) {
            current.setType(previous.getType());
        }
        if (current.getContractId() == null) {
            current.setContractId(previous.getContractId());
        }
        if (current.getAdminKey() == null) {
            current.setAdminKey(previous.getAdminKey());
        }
        if (current.getCreatedTimestamp() == null) {
            current.setCreatedTimestamp(previous.getCreatedTimestamp());
        }
        return current;
    }

    private NftAllowance mergeNftAllowance(NftAllowance previous, NftAllowance current) {
        previous.setTimestampUpper(current.getTimestampLower());
        return current;
    }

    private Node mergeNode(Node previous, Node current) {
        previous.setTimestampUpper(current.getTimestampLower());
        current.setCreatedTimestamp(previous.getCreatedTimestamp());

        if (current.getAccountId() == null) {
            current.setAccountId(previous.getAccountId());
        }

        if (current.getAdminKey() == null) {
            current.setAdminKey(previous.getAdminKey());
        }

        if (current.getDeclineReward() == null) {
            current.setDeclineReward(previous.getDeclineReward());
        }

        if (current.getGrpcProxyEndpoint() == null) {
            current.setGrpcProxyEndpoint(previous.getGrpcProxyEndpoint());
        }

        if (current.getAssociatedRegisteredNodes() == null) {
            current.setAssociatedRegisteredNodes(previous.getAssociatedRegisteredNodes());
        }

        return current;
    }

    private RegisteredNode mergeRegisteredNode(RegisteredNode previous, RegisteredNode current) {
        previous.setTimestampUpper(current.getTimestampLower());
        current.setCreatedTimestamp(previous.getCreatedTimestamp());

        if (current.getAdminKey() == null) {
            current.setAdminKey(previous.getAdminKey());
        }

        if (current.getDescription() == null) {
            current.setDescription(previous.getDescription());
        }

        if (current.getNodeAccount() == null) {
            current.setNodeAccount(previous.getNodeAccount());
        }

        if (current.getServiceEndpoints() == null) {
            current.setServiceEndpoints(previous.getServiceEndpoints());
        }

        return current;
    }

    private Schedule mergeSchedule(Schedule cachedSchedule, Schedule schedule) {
        cachedSchedule.setExecutedTimestamp(schedule.getExecutedTimestamp());
        return cachedSchedule;
    }

    /**
     * Merges two token objects into one. The previous may or not be an initial create with all fields while the current
     * will always be a partial update. Copy immutable fields from the previous to the current and close the previous'
     * timestamp range. Copy other fields from the previous if not set in current.
     *
     * @param previous token to merge into the current
     * @param current  token
     * @return the merged token
     */
    private Token mergeToken(Token previous, Token current) {

        if (!current.hasHistory()) {
            previous.setTotalSupply(current.getTotalSupply());
            return previous;
        }

        // When current has history, current should always have a null totalSupply,
        // Hence, it is safe to merge total supply from previous to current here.
        if (!previous.hasHistory()) {
            current.setTotalSupply(previous.getTotalSupply());
            return current;
        }

        previous.setTimestampUpper(current.getTimestampLower());

        current.setCreatedTimestamp(previous.getCreatedTimestamp());
        current.setDecimals(previous.getDecimals());
        current.setFreezeDefault(previous.getFreezeDefault());
        current.setFreezeStatus(previous.getFreezeStatus());
        current.setInitialSupply(previous.getInitialSupply());
        current.setKycStatus(previous.getKycStatus());
        current.setMaxSupply(previous.getMaxSupply());
        current.setSupplyType(previous.getSupplyType());
        current.setType(previous.getType());

        if (current.getFeeScheduleKey() == null) {
            current.setFeeScheduleKey(previous.getFeeScheduleKey());
        }

        if (current.getFreezeKey() == null) {
            current.setFreezeKey(previous.getFreezeKey());
        }

        if (current.getKycKey() == null) {
            current.setKycKey(previous.getKycKey());
        }

        if (current.getMetadata() == null) {
            current.setMetadata(previous.getMetadata());
        }

        if (current.getMetadataKey() == null) {
            current.setMetadataKey(previous.getMetadataKey());
        }

        if (current.getName() == null) {
            current.setName(previous.getName());
        }

        if (current.getPauseKey() == null) {
            current.setPauseKey(previous.getPauseKey());
        }

        if (current.getPauseStatus() == null) {
            current.setPauseStatus(previous.getPauseStatus());
        }

        if (current.getSupplyKey() == null) {
            current.setSupplyKey(previous.getSupplyKey());
        }

        if (current.getSymbol() == null) {
            current.setSymbol(previous.getSymbol());
        }

        if (current.getTreasuryAccountId() == null) {
            current.setTreasuryAccountId(previous.getTreasuryAccountId());
        }

        if (current.getWipeKey() == null) {
            current.setWipeKey(previous.getWipeKey());
        }

        // This method should not be called with negative total supply since wipe/burn/token dissociate of a deleted
        // token will not have history so will not reach here.
        current.setTotalSupply(previous.getTotalSupply());

        return current;
    }

    private TokenAccount mergeTokenAccount(TokenAccount lastTokenAccount, TokenAccount newTokenAccount) {
        if (!lastTokenAccount.hasHistory()) {
            if (newTokenAccount.hasHistory()) {
                lastTokenAccount.setAutomaticAssociation(newTokenAccount.getAutomaticAssociation());
                lastTokenAccount.setAssociated(newTokenAccount.getAssociated());
                lastTokenAccount.setCreatedTimestamp(newTokenAccount.getCreatedTimestamp());
                lastTokenAccount.setFreezeStatus(newTokenAccount.getFreezeStatus());
                lastTokenAccount.setKycStatus(newTokenAccount.getKycStatus());
                lastTokenAccount.setTimestampRange(newTokenAccount.getTimestampRange());
                return lastTokenAccount;
            }

            return mergeTokenAccountBalance(lastTokenAccount, newTokenAccount);
        }

        if (lastTokenAccount.hasHistory() && !newTokenAccount.hasHistory()) {
            return mergeTokenAccountBalance(lastTokenAccount, newTokenAccount);
        }

        if (lastTokenAccount.getTimestampRange().equals(newTokenAccount.getTimestampRange())) {
            // The token accounts are for the same range, accept the previous one
            // This is a workaround for https://github.com/hiero-ledger/hiero-consensus-node/issues/3240
            log.warn("Skipping duplicate token account association: {}", newTokenAccount);
            return lastTokenAccount;
        }

        lastTokenAccount.setTimestampUpper(newTokenAccount.getTimestampLower());

        if (newTokenAccount.getCreatedTimestamp() != null) {
            return newTokenAccount;
        }

        // newTokenAccount is a partial update. It must have its id (tokenId, accountId) set.
        // copy the lifespan immutable fields createdTimestamp and automaticAssociation from the previous snapshot.
        // copy other fields from the previous snapshot if not set in newTokenAccount
        newTokenAccount.setCreatedTimestamp(lastTokenAccount.getCreatedTimestamp());
        newTokenAccount.setBalance(lastTokenAccount.getBalance());
        if (lastTokenAccount.getBalanceTimestamp() != null) {
            newTokenAccount.setBalanceTimestamp(lastTokenAccount.getBalanceTimestamp());
        }

        newTokenAccount.setAutomaticAssociation(lastTokenAccount.getAutomaticAssociation());

        if (newTokenAccount.getAssociated() == null) {
            newTokenAccount.setAssociated(lastTokenAccount.getAssociated());
        }

        if (newTokenAccount.getFreezeStatus() == null) {
            newTokenAccount.setFreezeStatus(lastTokenAccount.getFreezeStatus());
        }

        if (newTokenAccount.getKycStatus() == null) {
            newTokenAccount.setKycStatus(lastTokenAccount.getKycStatus());
        }

        return newTokenAccount;
    }

    private TokenAccount mergeTokenAccountBalance(TokenAccount lastTokenAccount, TokenAccount newTokenAccount) {
        lastTokenAccount.setBalance(newTokenAccount.getBalance() + lastTokenAccount.getBalance());
        if (newTokenAccount.getBalanceTimestamp() != null) {
            lastTokenAccount.setBalanceTimestamp(newTokenAccount.getBalanceTimestamp());
        }
        return lastTokenAccount;
    }

    private TokenAirdrop mergeTokenAirdrop(TokenAirdrop previous, TokenAirdrop current) {
        if (previous.getAmount() != null && current.getAmount() == null) {
            // Cancel or claim do not contain an amount so set the amount here so as not to override it with null
            current.setAmount(previous.getAmount());
        }

        previous.setTimestampUpper(current.getTimestampLower());
        return current;
    }

    private Topic mergeTopic(Topic previous, Topic current) {
        current.setCreatedTimestamp(previous.getCreatedTimestamp());

        if (current.getAdminKey() == null) {
            current.setAdminKey(previous.getAdminKey());
        }

        if (current.getFeeExemptKeyList() == null) {
            current.setFeeExemptKeyList(previous.getFeeExemptKeyList());
        }

        if (current.getFeeScheduleKey() == null) {
            current.setFeeScheduleKey(previous.getFeeScheduleKey());
        }

        if (current.getSubmitKey() == null) {
            current.setSubmitKey(previous.getSubmitKey());
        }

        previous.setTimestampUpper(current.getTimestampLower());

        return current;
    }

    private HookStorage mergeHookStorage(HookStorage previous, HookStorage current) {

        if (previous.isDeleted() && !current.isDeleted()) {
            /* This is set to the negative timestamp to aid coalesce sql on the upsertable column
             we need to be able to identify the point where the hook storage lifecycle was started again
             in order to set the created timestamp correctly
            */
            previous.setCreatedTimestamp(~current.getCreatedTimestamp() + 1);
        }

        previous.setValue(current.getValue());
        previous.setModifiedTimestamp(current.getModifiedTimestamp());

        return previous;
    }

    private void onNftTransferList(Transaction transaction) {
        var nftTransferList = transaction.getNftTransfer();
        if (CollectionUtils.isEmpty(nftTransferList)) {
            return;
        }

        for (var nftTransfer : nftTransferList) {
            long tokenId = nftTransfer.getTokenId().getId();
            if (nftTransfer.getSerialNumber() == NftTransfer.WILDCARD_SERIAL_NUMBER) {
                // nft treasury change, there should be only one such nft transfer in the list
                flushNftState();
                nftRepository.updateTreasury(
                        transaction.getConsensusTimestamp(),
                        nftTransfer.getReceiverAccountId().getId(),
                        nftTransfer.getSenderAccountId().getId(),
                        nftTransfer.getTokenId().getId());
                return;
            }

            if (!entityProperties.getPersist().isTrackBalance()) {
                return;
            }

            if (!EntityId.isEmpty(nftTransfer.getSenderAccountId())) {
                var tokenAccount = new TokenAccount();
                tokenAccount.setAccountId(nftTransfer.getSenderAccountId().getId());
                tokenAccount.setAssociated(true);
                tokenAccount.setTokenId(tokenId);
                tokenAccount.setBalance(-1);
                tokenAccount.setBalanceTimestamp(transaction.getConsensusTimestamp());
                onTokenAccount(tokenAccount);
            }

            if (!EntityId.isEmpty(nftTransfer.getReceiverAccountId())) {
                var tokenAccount = new TokenAccount();
                tokenAccount.setAccountId(nftTransfer.getReceiverAccountId().getId());
                tokenAccount.setAssociated(true);
                tokenAccount.setTokenId(tokenId);
                tokenAccount.setBalance(1);
                tokenAccount.setBalanceTimestamp(transaction.getConsensusTimestamp());
                onTokenAccount(tokenAccount);
            }
        }
    }
}
