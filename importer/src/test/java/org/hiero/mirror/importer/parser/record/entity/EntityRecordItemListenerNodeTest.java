// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.node.ServiceEndpoint;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.NetworkStakeRepository;
import org.hiero.mirror.importer.repository.NodeRepository;
import org.hiero.mirror.importer.repository.NodeStakeRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class EntityRecordItemListenerNodeTest extends AbstractEntityRecordItemListenerTest {

    private final NodeRepository nodeRepository;
    private final NetworkStakeRepository networkStakeRepository;
    private final NodeStakeRepository nodeStakeRepository;

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
                () -> assertEquals(3, cryptoTransferRepository.count()),
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
        var recordItem = recordItemBuilder.nodeCreate().build();
        var nodeCreate = recordItem.getTransactionBody().getNodeCreate();
        var protoEndpoint = nodeCreate.getGrpcProxyEndpoint();
        var expectedNode = Node.builder()
                .accountId(EntityId.of(nodeCreate.getAccountId()))
                .adminKey(nodeCreate.getAdminKey().toByteArray())
                .associatedRegisteredNodes(List.copyOf(nodeCreate.getAssociatedRegisteredNodeList()))
                .createdTimestamp(recordItem.getConsensusTimestamp())
                .declineReward(false)
                .grpcProxyEndpoint(ServiceEndpoint.builder()
                        .domainName(protoEndpoint.getDomainName())
                        .ipAddressV4("")
                        .port(protoEndpoint.getPort())
                        .build())
                .nodeId(recordItem.getTransactionRecord().getReceipt().getNodeId())
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();

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
        var recordItem = recordItemBuilder
                .nodeUpdate()
                .transactionBody(b -> b.setDeclineReward(BoolValue.of(true)))
                .build();
        var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        var timestamp = recordItem.getConsensusTimestamp() - 1;
        var node = domainBuilder
                .node()
                .customize(n -> n.createdTimestamp(timestamp)
                        .declineReward(false)
                        .nodeId(nodeUpdate.getNodeId())
                        .timestampRange(Range.atLeast(timestamp)))
                .persist();

        var expectedNode = Node.builder()
                .accountId(EntityId.of(nodeUpdate.getAccountId()))
                .adminKey(nodeUpdate.getAdminKey().toByteArray())
                .associatedRegisteredNodes(node.getAssociatedRegisteredNodes())
                .createdTimestamp(node.getCreatedTimestamp())
                .declineReward(nodeUpdate.getDeclineReward().getValue())
                .grpcProxyEndpoint(ServiceEndpoint.builder()
                        .domainName(nodeUpdate.getGrpcProxyEndpoint().getDomainName())
                        .ipAddressV4("")
                        .port(nodeUpdate.getGrpcProxyEndpoint().getPort())
                        .build())
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
    void nodeUpdateUnsetGrpcProxyEndpoint() {
        var recordItem = recordItemBuilder
                .nodeUpdate()
                .transactionBody(b -> b.clearAccountId()
                        .clearAdminKey()
                        .setGrpcProxyEndpoint(com.hederahashgraph.api.proto.java.ServiceEndpoint.getDefaultInstance()))
                .build();
        var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        var timestamp = recordItem.getConsensusTimestamp() - 1;
        var node = domainBuilder
                .node()
                .customize(n -> n.createdTimestamp(timestamp)
                        .nodeId(nodeUpdate.getNodeId())
                        .timestampRange(Range.atLeast(timestamp)))
                .persist();

        var expectedNode = node.toBuilder()
                .grpcProxyEndpoint(null) // Should clear
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();

        parseRecordItemAndCommit(recordItem);

        node.setTimestampUpper(recordItem.getConsensusTimestamp());

        softly.assertThat(nodeRepository.findAll()).containsExactly(expectedNode);
        softly.assertThat(findHistory(Node.class)).containsExactly(node);
    }

    @Test
    void nodeUpdateNoChange() {
        var recordItem = recordItemBuilder
                .nodeUpdate()
                .transactionBody(NodeUpdateTransactionBody.Builder::clearAccountId)
                .transactionBody(NodeUpdateTransactionBody.Builder::clearAdminKey)
                .transactionBody(NodeUpdateTransactionBody.Builder::clearDeclineReward)
                .transactionBody(NodeUpdateTransactionBody.Builder::clearGrpcProxyEndpoint)
                .build();
        var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        var timestamp = recordItem.getConsensusTimestamp() - 1;
        var node = domainBuilder
                .node()
                .customize(n -> n.createdTimestamp(timestamp)
                        .declineReward(true)
                        .nodeId(nodeUpdate.getNodeId())
                        .timestampRange(Range.atLeast(timestamp)))
                .persist();

        var expectedNode = node.toBuilder()
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
        var node = domainBuilder.node().persist();
        var recordItem = recordItemBuilder
                .nodeDelete()
                .receipt(r -> r.setNodeId(node.getNodeId()))
                .transactionBody(b -> b.setNodeId(node.getNodeId()))
                .build();
        var deletedNode = node.toBuilder()
                .deleted(true)
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
        softly.assertThat(nodeRepository.findAll()).containsExactly(deletedNode);
        softly.assertThat(findHistory(Node.class)).containsExactly(node);
    }

    @Test
    void nodeCreateWithAssociatedRegisteredNodes() {
        final var registeredNodeId1 = domainBuilder.id();
        final var registeredNodeId2 = domainBuilder.id();
        final var recordItem = recordItemBuilder
                .nodeCreate()
                .transactionBody(b ->
                        b.addAssociatedRegisteredNode(registeredNodeId1).addAssociatedRegisteredNode(registeredNodeId2))
                .build();
        final var nodeCreate = recordItem.getTransactionBody().getNodeCreate();
        final var protoEndpoint = nodeCreate.getGrpcProxyEndpoint();
        final var expectedNode = Node.builder()
                .accountId(EntityId.of(nodeCreate.getAccountId()))
                .adminKey(nodeCreate.getAdminKey().toByteArray())
                .associatedRegisteredNodes(List.of(registeredNodeId1, registeredNodeId2))
                .createdTimestamp(recordItem.getConsensusTimestamp())
                .declineReward(false)
                .grpcProxyEndpoint(ServiceEndpoint.builder()
                        .domainName(protoEndpoint.getDomainName())
                        .ipAddressV4("")
                        .port(protoEndpoint.getPort())
                        .build())
                .nodeId(recordItem.getTransactionRecord().getReceipt().getNodeId())
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();

        parseRecordItemAndCommit(recordItem);

        softly.assertThat(nodeRepository.findAll()).containsExactly(expectedNode);
    }

    @Test
    void nodeUpdateWithAssociatedRegisteredNodes() {
        final var registeredNodeId1 = domainBuilder.id();
        final var registeredNodeId2 = domainBuilder.id();
        final var recordItem = recordItemBuilder
                .nodeUpdate()
                .transactionBody(b ->
                        b.addAssociatedRegisteredNode(registeredNodeId1).addAssociatedRegisteredNode(registeredNodeId2))
                .build();
        final var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        final var timestamp = recordItem.getConsensusTimestamp() - 1;
        final var node = domainBuilder
                .node()
                .customize(n -> n.createdTimestamp(timestamp)
                        .nodeId(nodeUpdate.getNodeId())
                        .timestampRange(Range.atLeast(timestamp)))
                .persist();

        final var expectedNode = Node.builder()
                .accountId(EntityId.of(nodeUpdate.getAccountId()))
                .adminKey(nodeUpdate.getAdminKey().toByteArray())
                .associatedRegisteredNodes(List.of(registeredNodeId1, registeredNodeId2))
                .createdTimestamp(node.getCreatedTimestamp())
                .declineReward(nodeUpdate.getDeclineReward().getValue())
                .grpcProxyEndpoint(ServiceEndpoint.builder()
                        .domainName(nodeUpdate.getGrpcProxyEndpoint().getDomainName())
                        .ipAddressV4("")
                        .port(nodeUpdate.getGrpcProxyEndpoint().getPort())
                        .build())
                .nodeId(node.getNodeId())
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();

        parseRecordItemAndCommit(recordItem);

        node.setTimestampUpper(recordItem.getConsensusTimestamp());

        softly.assertThat(nodeRepository.findAll()).containsExactly(expectedNode);
        softly.assertThat(findHistory(Node.class)).containsExactly(node);
    }
}
