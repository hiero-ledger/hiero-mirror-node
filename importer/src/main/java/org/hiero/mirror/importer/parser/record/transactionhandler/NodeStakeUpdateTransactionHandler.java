// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;
import org.springframework.context.ApplicationEventPublisher;

@CustomLog
@Named
@RequiredArgsConstructor
class NodeStakeUpdateTransactionHandler extends AbstractTransactionHandler {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ConsensusNodeService consensusNodeService;
    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.NODESTAKEUPDATE;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        if (!recordItem.isSuccessful()) {
            var status = recordItem.getTransactionRecord().getReceipt().getStatus();
            log.warn("NodeStakeUpdateTransaction at {} failed with status {}", consensusTimestamp, status);
            return;
        }

        // We subtract one since we get stake update in current day, but it applies to previous day
        long epochDay = Utility.getEpochDay(consensusTimestamp) - 1L;
        var transactionBody = recordItem.getTransactionBody().getNodeStakeUpdate();
        long stakingPeriod = DomainUtils.timestampInNanosMax(transactionBody.getEndOfStakingPeriod());
        long stakeTotal = transactionBody.getNodeStakeList().stream()
                .map(nodeStake -> nodeStake.getStakeRewarded() + nodeStake.getStakeNotRewarded())
                .reduce(0L, Long::sum);

        NetworkStake networkStake = new NetworkStake();
        networkStake.setConsensusTimestamp(consensusTimestamp);
        networkStake.setEpochDay(epochDay);
        networkStake.setMaxStakeRewarded(transactionBody.getMaxStakeRewarded());
        networkStake.setMaxStakingRewardRatePerHbar(transactionBody.getMaxStakingRewardRatePerHbar());
        networkStake.setMaxTotalReward(transactionBody.getMaxTotalReward());
        networkStake.setNodeRewardFeeDenominator(
                transactionBody.getNodeRewardFeeFraction().getDenominator());
        networkStake.setNodeRewardFeeNumerator(
                transactionBody.getNodeRewardFeeFraction().getNumerator());
        networkStake.setReservedStakingRewards(transactionBody.getReservedStakingRewards());
        networkStake.setRewardBalanceThreshold(transactionBody.getRewardBalanceThreshold());
        networkStake.setStakeTotal(stakeTotal);
        networkStake.setStakingPeriod(stakingPeriod);
        networkStake.setStakingPeriodDuration(transactionBody.getStakingPeriod());
        networkStake.setStakingPeriodsStored(transactionBody.getStakingPeriodsStored());
        networkStake.setStakingRewardFeeDenominator(
                transactionBody.getStakingRewardFeeFraction().getDenominator());
        networkStake.setStakingRewardFeeNumerator(
                transactionBody.getStakingRewardFeeFraction().getNumerator());
        networkStake.setStakingRewardRate(transactionBody.getStakingRewardRate());
        networkStake.setStakingStartThreshold(transactionBody.getStakingStartThreshold());
        networkStake.setUnreservedStakingRewardBalance(transactionBody.getUnreservedStakingRewardBalance());

        entityListener.onNetworkStake(networkStake);

        var nodeStakesProtos = transactionBody.getNodeStakeList();
        if (nodeStakesProtos.isEmpty()) {
            log.warn("NodeStakeUpdateTransaction has empty node stake list");
            return;
        }

        for (var nodeStakeProto : nodeStakesProtos) {
            var nodeStake = new NodeStake();
            nodeStake.setConsensusTimestamp(consensusTimestamp);
            nodeStake.setEpochDay(epochDay);
            nodeStake.setMaxStake(nodeStakeProto.getMaxStake());
            nodeStake.setMinStake(nodeStakeProto.getMinStake());
            nodeStake.setNodeId(nodeStakeProto.getNodeId());
            nodeStake.setRewardRate(nodeStakeProto.getRewardRate());
            nodeStake.setStake(nodeStakeProto.getStake());
            nodeStake.setStakeNotRewarded(nodeStakeProto.getStakeNotRewarded());
            nodeStake.setStakeRewarded(nodeStakeProto.getStakeRewarded());
            nodeStake.setStakingPeriod(stakingPeriod);
            entityListener.onNodeStake(nodeStake);
        }

        applicationEventPublisher.publishEvent(new NodeStakeUpdatedEvent(this));
        consensusNodeService.refresh();
    }
}
