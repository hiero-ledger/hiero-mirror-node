// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.addressbook.ConsensusNodeService;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

class NodeStakeUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Captor
    private ArgumentCaptor<NetworkStake> networkStakes;

    @Captor
    private ArgumentCaptor<NodeStake> nodeStakes;

    @Mock
    private ConsensusNodeService consensusNodeService;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeStakeUpdateTransactionHandler(applicationEventPublisher, consensusNodeService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setNodeStakeUpdate(NodeStakeUpdateTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void testGetEntity() {
        assertThat(transactionHandler.getEntity(null)).isNull();
    }

    @Test
    void getType() {
        assertThat(transactionHandler.getType()).isEqualTo(TransactionType.NODESTAKEUPDATE);
    }

    @SuppressWarnings("deprecation")
    @Test
    void updateTransactionSuccessful() {
        // given
        long nodeStake1 = 5_000_000 * TINYBARS_IN_ONE_HBAR;
        var nodeStakeProto1 = getNodeStakeProto(nodeStake1, nodeStake1, 0);

        long nodeStake2 = 21_000_000 * TINYBARS_IN_ONE_HBAR;
        long nodeStakeRewarded2 = nodeStake2 - 3_000 * TINYBARS_IN_ONE_HBAR;
        long nodeNotRewarded2 = 2_0000 * TINYBARS_IN_ONE_HBAR;
        var nodeStakeProto2 = getNodeStakeProto(nodeStake2, nodeStakeRewarded2, nodeNotRewarded2);

        var recordItem = recordItemBuilder
                .nodeStakeUpdate()
                .transactionBody(
                        b -> b.clearNodeStake().addNodeStake(nodeStakeProto1).addNodeStake(nodeStakeProto2))
                .build();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long epochDay = Utility.getEpochDay(consensusTimestamp) - 1L;
        long stakeTotal = nodeStake1 + nodeStakeRewarded2 + nodeNotRewarded2;
        var body = recordItem.getTransactionBody().getNodeStakeUpdate();
        long stakingPeriod = DomainUtils.timestampInNanosMax(body.getEndOfStakingPeriod());
        var expectedNodeStakes = List.of(
                getExpectedNodeStake(consensusTimestamp, epochDay, nodeStakeProto1, stakingPeriod),
                getExpectedNodeStake(consensusTimestamp, epochDay, nodeStakeProto2, stakingPeriod));
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(applicationEventPublisher).publishEvent(any(NodeStakeUpdatedEvent.class));
        verify(entityListener).onNetworkStake(networkStakes.capture());
        verify(entityListener, times(2)).onNodeStake(nodeStakes.capture());
        assertThat(nodeStakes.getAllValues()).containsExactlyInAnyOrderElementsOf(expectedNodeStakes);
        verify(consensusNodeService).refresh();
        assertThat(networkStakes.getAllValues())
                .hasSize(1)
                .first()
                .returns(consensusTimestamp, NetworkStake::getConsensusTimestamp)
                .returns(epochDay, NetworkStake::getEpochDay)
                .returns(body.getMaxStakeRewarded(), NetworkStake::getMaxStakeRewarded)
                .returns(body.getMaxStakingRewardRatePerHbar(), NetworkStake::getMaxStakingRewardRatePerHbar)
                .returns(body.getMaxTotalReward(), NetworkStake::getMaxTotalReward)
                .returns(body.getNodeRewardFeeFraction().getDenominator(), NetworkStake::getNodeRewardFeeDenominator)
                .returns(body.getNodeRewardFeeFraction().getNumerator(), NetworkStake::getNodeRewardFeeNumerator)
                .returns(body.getReservedStakingRewards(), NetworkStake::getReservedStakingRewards)
                .returns(body.getRewardBalanceThreshold(), NetworkStake::getRewardBalanceThreshold)
                .returns(stakeTotal, NetworkStake::getStakeTotal)
                .returns(stakingPeriod, NetworkStake::getStakingPeriod)
                .returns(body.getStakingPeriod(), NetworkStake::getStakingPeriodDuration)
                .returns(body.getStakingPeriodsStored(), NetworkStake::getStakingPeriodsStored)
                .returns(
                        body.getStakingRewardFeeFraction().getDenominator(),
                        NetworkStake::getStakingRewardFeeDenominator)
                .returns(body.getStakingRewardFeeFraction().getNumerator(), NetworkStake::getStakingRewardFeeNumerator)
                .returns(body.getStakingRewardRate(), NetworkStake::getStakingRewardRate)
                .returns(body.getStakingStartThreshold(), NetworkStake::getStakingStartThreshold)
                .returns(body.getUnreservedStakingRewardBalance(), NetworkStake::getUnreservedStakingRewardBalance);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionNoStake() {
        // given
        var recordItem = recordItemBuilder
                .nodeStakeUpdate()
                .transactionBody(Builder::clearNodeStake)
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t ->
                        t.consensusTimestamp(recordItem.getConsensusTimestamp()).entityId(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(applicationEventPublisher);
        verify(entityListener).onNetworkStake(any());
        verify(entityListener, never()).onNodeStake(any());
        verify(consensusNodeService, never()).refresh();
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    private com.hederahashgraph.api.proto.java.NodeStake getNodeStakeProto(
            long stake, long stakeRewarded, long stakeNotRewarded) {
        return recordItemBuilder
                .nodeStake()
                .setMaxStake(stake * 2)
                .setMinStake(stake / 2)
                .setStake(stake)
                .setStakeNotRewarded(stakeNotRewarded)
                .setStakeRewarded(stakeRewarded)
                .build();
    }

    private NodeStake getExpectedNodeStake(
            long consensusTimestamp,
            long epochDay,
            com.hederahashgraph.api.proto.java.NodeStake nodeStakeProto,
            long stakingPeriod) {
        return NodeStake.builder()
                .consensusTimestamp(consensusTimestamp)
                .epochDay(epochDay)
                .maxStake(nodeStakeProto.getMaxStake())
                .minStake(nodeStakeProto.getMinStake())
                .nodeId(nodeStakeProto.getNodeId())
                .rewardRate(nodeStakeProto.getRewardRate())
                .stake(nodeStakeProto.getStake())
                .stakeNotRewarded(nodeStakeProto.getStakeNotRewarded())
                .stakeRewarded(nodeStakeProto.getStakeRewarded())
                .stakingPeriod(stakingPeriod)
                .build();
    }
}
