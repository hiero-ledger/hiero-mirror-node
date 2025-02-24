// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.addressbook.NetworkStake;
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.entity.Node;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.repository.NetworkStakeRepository;
import com.hedera.mirror.importer.repository.NodeRepository;
import com.hedera.mirror.importer.repository.NodeStakeRepository;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityRecordItemListenerNodeTest extends AbstractEntityRecordItemListenerTest {

    private final NodeRepository nodeRepository;
    private final NetworkStakeRepository networkStakeRepository;
    private final NodeStakeRepository nodeStakeRepository;

    private static Node getExpectedNode(RecordItem recordItem) {
        return Node.builder()
                .createdTimestamp(recordItem.getConsensusTimestamp())
                .nodeId(recordItem.getTransactionRecord().getReceipt().getNodeId())
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();
    }

    @AfterEach
    void teardown() {
        entityProperties.getPersist().setNodes(false);
    }

    @SuppressWarnings("deprecation")
    @Test
    void nodeStakeUpdate() {
        var recordItem = recordItemBuilder.nodeStakeUpdate().build();
        var body = recordItem.getTransactionBody().getNodeStakeUpdate();
        var nodeStake = body.getNodeStakeList().get(0);
        var stakingPeriod = DomainUtils.timestampInNanosMax(body.getEndOfStakingPeriod());
        var epochDay = Utility.getEpochDay(recordItem.getConsensusTimestamp()) - 1L;

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertThat(nodeStakeRepository.findAll())
                        .hasSize(body.getNodeStakeCount())
                        .first()
                        .isNotNull()
                        .returns(recordItem.getConsensusTimestamp(), NodeStake::getConsensusTimestamp)
                        .returns(epochDay, NodeStake::getEpochDay)
                        .returns(nodeStake.getNodeId(), NodeStake::getNodeId)
                        .returns(nodeStake.getRewardRate(), NodeStake::getRewardRate)
                        .returns(nodeStake.getStake(), NodeStake::getStake)
                        .returns(nodeStake.getStakeRewarded(), NodeStake::getStakeRewarded)
                        .returns(stakingPeriod, NodeStake::getStakingPeriod),
                () -> assertThat(networkStakeRepository.findAll())
                        .hasSize(1)
                        .first()
                        .returns(recordItem.getConsensusTimestamp(), NetworkStake::getConsensusTimestamp)
                        .returns(epochDay, NetworkStake::getEpochDay)
                        .returns(body.getMaxStakeRewarded(), NetworkStake::getMaxStakeRewarded)
                        .returns(body.getMaxStakingRewardRatePerHbar(), NetworkStake::getMaxStakingRewardRatePerHbar)
                        .returns(body.getMaxTotalReward(), NetworkStake::getMaxTotalReward)
                        .returns(
                                body.getNodeRewardFeeFraction().getDenominator(),
                                NetworkStake::getNodeRewardFeeDenominator)
                        .returns(
                                body.getNodeRewardFeeFraction().getNumerator(), NetworkStake::getNodeRewardFeeNumerator)
                        .returns(body.getReservedStakingRewards(), NetworkStake::getReservedStakingRewards)
                        .returns(body.getRewardBalanceThreshold(), NetworkStake::getRewardBalanceThreshold)
                        .returns(nodeStake.getStake(), NetworkStake::getStakeTotal)
                        .returns(stakingPeriod, NetworkStake::getStakingPeriod)
                        .returns(body.getStakingPeriod(), NetworkStake::getStakingPeriodDuration)
                        .returns(body.getStakingPeriodsStored(), NetworkStake::getStakingPeriodsStored)
                        .returns(
                                body.getStakingRewardFeeFraction().getDenominator(),
                                NetworkStake::getStakingRewardFeeDenominator)
                        .returns(
                                body.getStakingRewardFeeFraction().getNumerator(),
                                NetworkStake::getStakingRewardFeeNumerator)
                        .returns(body.getStakingRewardRate(), NetworkStake::getStakingRewardRate)
                        .returns(body.getStakingStartThreshold(), NetworkStake::getStakingStartThreshold)
                        .returns(
                                body.getUnreservedStakingRewardBalance(),
                                NetworkStake::getUnreservedStakingRewardBalance));
    }

    @Test
    void nodeCreate() {
        entityProperties.getPersist().setNodes(true);
        var recordItem = recordItemBuilder.nodeCreate().build();
        var expectedNode = getExpectedNode(recordItem);
        expectedNode.setAdminKey(
                recordItem.getTransactionBody().getNodeCreate().getAdminKey().toByteArray());

        parseRecordItemAndCommit(recordItem);

        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(transactionRepository.findAll())
                .hasSize(1)
                .first()
                .isNotNull()
                .returns(recordItem.getTransaction().toByteArray(), Transaction::getTransactionBytes)
                .returns(recordItem.getTransactionRecord().toByteArray(), Transaction::getTransactionRecordBytes);
        softly.assertThat(nodeRepository.findAll()).containsExactly(expectedNode);
    }

    @Test
    void nodeUpdate() {
        entityProperties.getPersist().setNodes(true);
        var recordItem = recordItemBuilder.nodeUpdate().build();
        var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        var timestamp = recordItem.getConsensusTimestamp() - 1;
        var node = domainBuilder
                .node()
                .customize(n -> n.createdTimestamp(timestamp)
                        .nodeId(nodeUpdate.getNodeId())
                        .timestampRange(Range.atLeast(timestamp)))
                .persist();

        var expectedNode = Node.builder()
                .adminKey(nodeUpdate.getAdminKey().toByteArray())
                .createdTimestamp(node.getCreatedTimestamp())
                .nodeId(node.getNodeId())
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();

        parseRecordItemAndCommit(recordItem);

        node.setTimestampUpper(recordItem.getConsensusTimestamp());

        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(transactionRepository.findAll())
                .hasSize(1)
                .first()
                .isNotNull()
                .returns(recordItem.getTransaction().toByteArray(), Transaction::getTransactionBytes)
                .returns(recordItem.getTransactionRecord().toByteArray(), Transaction::getTransactionRecordBytes);
        softly.assertThat(nodeRepository.findAll()).containsExactly(expectedNode);
        softly.assertThat(findHistory(Node.class)).containsExactly(node);
    }

    @Test
    void nodeUpdateWithoutAdminKeyUpdate() {
        entityProperties.getPersist().setNodes(true);

        var recordItem = recordItemBuilder
                .nodeUpdate()
                .transactionBody(NodeUpdateTransactionBody.Builder::clearAdminKey)
                .build();
        var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        var timestamp = recordItem.getConsensusTimestamp() - 1;
        var node = domainBuilder
                .node()
                .customize(n -> n.createdTimestamp(timestamp)
                        .nodeId(nodeUpdate.getNodeId())
                        .timestampRange(Range.atLeast(timestamp)))
                .persist();

        var expectedNode = Node.builder()
                .adminKey(node.getAdminKey())
                .createdTimestamp(node.getCreatedTimestamp())
                .nodeId(node.getNodeId())
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();

        parseRecordItemAndCommit(recordItem);

        node.setTimestampUpper(recordItem.getConsensusTimestamp());

        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(transactionRepository.findAll())
                .hasSize(1)
                .first()
                .isNotNull()
                .returns(recordItem.getTransaction().toByteArray(), Transaction::getTransactionBytes)
                .returns(recordItem.getTransactionRecord().toByteArray(), Transaction::getTransactionRecordBytes);
        softly.assertThat(nodeRepository.findAll()).containsExactly(expectedNode);
        softly.assertThat(findHistory(Node.class)).containsExactly(node);
    }

    @Test
    void nodeDelete() {
        entityProperties.getPersist().setNodes(true);
        var node = domainBuilder.node().persist();
        var recordItem = recordItemBuilder
                .nodeDelete()
                .receipt(r -> r.setNodeId(node.getNodeId()))
                .transactionBody(b -> b.setNodeId(node.getNodeId()))
                .build();
        var expectedNode = getExpectedNode(recordItem);
        expectedNode.setDeleted(true);
        expectedNode.setCreatedTimestamp(node.getCreatedTimestamp());
        expectedNode.setAdminKey(node.getAdminKey());

        parseRecordItemAndCommit(recordItem);

        node.setTimestampUpper(recordItem.getConsensusTimestamp());

        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(transactionRepository.findAll())
                .hasSize(1)
                .first()
                .isNotNull()
                .returns(recordItem.getTransaction().toByteArray(), Transaction::getTransactionBytes)
                .returns(recordItem.getTransactionRecord().toByteArray(), Transaction::getTransactionRecordBytes);
        softly.assertThat(nodeRepository.findAll()).containsExactly(expectedNode);
        softly.assertThat(findHistory(Node.class)).containsExactly(node);
    }
}
