// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import java.util.Collection;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.contract.ContractLog;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.hook.Hook;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.domain.ledger.Ledger;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.schedule.Schedule;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.token.Nft;
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
import org.hiero.mirror.common.domain.transaction.StakingRewardTransfer;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.hiero.mirror.importer.exception.ImporterException;

/**
 * Handlers for items parsed during processing of record stream.
 */
public interface EntityListener {

    default boolean isEnabled() {
        return true;
    }

    default void onAssessedCustomFee(AssessedCustomFee assessedCustomFee) throws ImporterException {}

    default void onContract(Contract contract) {}

    default void onContractAction(ContractAction contractAction) {}

    default void onContractLog(ContractLog contractLog) {}

    default void onContractResult(ContractResult contractResult) throws ImporterException {}

    default void onContractStateChange(ContractStateChange contractStateChange) {}

    default void onContractTransactions(Collection<ContractTransaction> contractTransactions) {}

    default void onCryptoAllowance(CryptoAllowance cryptoAllowance) {}

    default void onCustomFee(CustomFee customFee) throws ImporterException {}

    default void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {}

    default void onEntity(Entity entity) throws ImporterException {}

    default void onEntityTransactions(Collection<EntityTransaction> entityTransactions) throws ImporterException {}

    default void onEthereumTransaction(EthereumTransaction ethereumTransaction) {}

    default void onFileData(FileData fileData) throws ImporterException {}

    default void onHook(Hook hook) {}

    default void onHookStorageChange(HookStorageChange storageChange) throws ImporterException {}

    default void onLedger(Ledger ledger) throws ImporterException {}

    default void onLiveHash(LiveHash liveHash) throws ImporterException {}

    default void onNetworkFreeze(NetworkFreeze networkFreeze) {}

    default void onNetworkStake(NetworkStake networkStake) throws ImporterException {}

    default void onNft(Nft nft) throws ImporterException {}

    default void onNftAllowance(NftAllowance nftAllowance) {}

    default void onNode(Node node) throws ImporterException {}

    default void onNodeStake(NodeStake nodeStake) throws ImporterException {}

    default void onPrng(Prng prng) {}

    default void onSchedule(Schedule schedule) throws ImporterException {}

    default void onStakingRewardTransfer(StakingRewardTransfer stakingRewardTransfer) {}

    default void onToken(Token token) throws ImporterException {}

    default void onTokenAccount(TokenAccount tokenAccount) throws ImporterException {}

    default void onTokenAirdrop(TokenAirdrop tokenAirdrop) {}

    default void onTokenAllowance(TokenAllowance tokenAllowance) {}

    default void onTokenTransfer(TokenTransfer tokenTransfer) throws ImporterException {}

    default void onTopic(Topic topic) throws ImporterException {}

    default void onTopicMessage(TopicMessage topicMessage) throws ImporterException {}

    default void onTransaction(Transaction transaction) throws ImporterException {}

    default void onTransactionSignature(TransactionSignature transactionSignature) throws ImporterException {}
}
