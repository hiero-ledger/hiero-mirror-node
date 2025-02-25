// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity;

import com.hedera.mirror.common.domain.addressbook.NetworkStake;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.contract.ContractTransaction;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.Node;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.common.domain.topic.Topic;
import com.hedera.mirror.common.domain.topic.TopicMessage;
import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;
import com.hedera.mirror.common.domain.transaction.CryptoTransfer;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hedera.mirror.common.domain.transaction.NetworkFreeze;
import com.hedera.mirror.common.domain.transaction.Prng;
import com.hedera.mirror.common.domain.transaction.StakingRewardTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionSignature;
import com.hedera.mirror.importer.exception.ImporterException;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;

@CustomLog
@Named
@Primary
@RequiredArgsConstructor
public class CompositeEntityListener implements EntityListener {

    private final List<EntityListener> entityListeners;

    private <T> void onEach(BiConsumer<EntityListener, T> consumer, T t) {
        for (int i = 0; i < entityListeners.size(); ++i) {
            var entityListener = entityListeners.get(i);
            if (entityListener.isEnabled()) {
                consumer.accept(entityListener, t);
            }
        }
    }

    @Override
    public void onAssessedCustomFee(AssessedCustomFee assessedCustomFee) throws ImporterException {
        onEach(EntityListener::onAssessedCustomFee, assessedCustomFee);
    }

    @Override
    public void onContract(Contract contract) throws ImporterException {
        onEach(EntityListener::onContract, contract);
    }

    @Override
    public void onContractAction(ContractAction contractAction) throws ImporterException {
        onEach(EntityListener::onContractAction, contractAction);
    }

    @Override
    public void onContractLog(ContractLog contractLog) throws ImporterException {
        onEach(EntityListener::onContractLog, contractLog);
    }

    @Override
    public void onContractResult(ContractResult contractResult) throws ImporterException {
        onEach(EntityListener::onContractResult, contractResult);
    }

    @Override
    public void onContractStateChange(ContractStateChange contractStateChange) throws ImporterException {
        onEach(EntityListener::onContractStateChange, contractStateChange);
    }

    @Override
    public void onContractTransactions(Collection<ContractTransaction> contractTransactions) {
        onEach(EntityListener::onContractTransactions, contractTransactions);
    }

    @Override
    public void onCryptoAllowance(CryptoAllowance cryptoAllowance) throws ImporterException {
        onEach(EntityListener::onCryptoAllowance, cryptoAllowance);
    }

    @Override
    public void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
        onEach(EntityListener::onCryptoTransfer, cryptoTransfer);
    }

    @Override
    public void onCustomFee(CustomFee customFee) throws ImporterException {
        onEach(EntityListener::onCustomFee, customFee);
    }

    @Override
    public void onEntity(Entity entity) throws ImporterException {
        onEach(EntityListener::onEntity, entity);
    }

    @Override
    public void onEntityTransactions(Collection<EntityTransaction> entityTransactions) throws ImporterException {
        onEach(EntityListener::onEntityTransactions, entityTransactions);
    }

    @Override
    public void onEthereumTransaction(EthereumTransaction ethereumTransaction) {
        onEach(EntityListener::onEthereumTransaction, ethereumTransaction);
    }

    @Override
    public void onFileData(FileData fileData) throws ImporterException {
        onEach(EntityListener::onFileData, fileData);
    }

    @Override
    public void onLiveHash(LiveHash liveHash) throws ImporterException {
        onEach(EntityListener::onLiveHash, liveHash);
    }

    @Override
    public void onNetworkFreeze(NetworkFreeze networkFreeze) {
        onEach(EntityListener::onNetworkFreeze, networkFreeze);
    }

    @Override
    public void onNetworkStake(NetworkStake networkStake) throws ImporterException {
        onEach(EntityListener::onNetworkStake, networkStake);
    }

    @Override
    public void onNft(Nft nft) throws ImporterException {
        onEach(EntityListener::onNft, nft);
    }

    @Override
    public void onNftAllowance(NftAllowance nftAllowance) throws ImporterException {
        onEach(EntityListener::onNftAllowance, nftAllowance);
    }

    @Override
    public void onNode(Node node) throws ImporterException {
        onEach(EntityListener::onNode, node);
    }

    @Override
    public void onNodeStake(NodeStake nodeStake) throws ImporterException {
        onEach(EntityListener::onNodeStake, nodeStake);
    }

    @Override
    public void onPrng(Prng prng) {
        onEach(EntityListener::onPrng, prng);
    }

    @Override
    public void onSchedule(Schedule schedule) throws ImporterException {
        onEach(EntityListener::onSchedule, schedule);
    }

    @Override
    public void onStakingRewardTransfer(StakingRewardTransfer stakingRewardTransfer) {
        onEach(EntityListener::onStakingRewardTransfer, stakingRewardTransfer);
    }

    @Override
    public void onToken(Token token) throws ImporterException {
        onEach(EntityListener::onToken, token);
    }

    @Override
    public void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {
        onEach(EntityListener::onTokenAccount, tokenAccount);
    }

    @Override
    public void onTokenAirdrop(TokenAirdrop tokenAirdrop) throws ImporterException {
        onEach(EntityListener::onTokenAirdrop, tokenAirdrop);
    }

    @Override
    public void onTokenAllowance(TokenAllowance tokenAllowance) throws ImporterException {
        onEach(EntityListener::onTokenAllowance, tokenAllowance);
    }

    @Override
    public void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {
        onEach(EntityListener::onTokenTransfer, tokenTransfer);
    }

    @Override
    public void onTopic(Topic topic) throws ImporterException {
        onEach(EntityListener::onTopic, topic);
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        onEach(EntityListener::onTopicMessage, topicMessage);
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        onEach(EntityListener::onTransaction, transaction);
    }

    @Override
    public void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {
        onEach(EntityListener::onTransactionSignature, transactionSignature);
    }
}
