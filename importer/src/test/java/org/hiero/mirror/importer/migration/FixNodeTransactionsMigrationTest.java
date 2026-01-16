// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.hiero.mirror.importer.repository.NodeRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@Tag("migration")
@RequiredArgsConstructor
class FixNodeTransactionsMigrationTest extends ImporterIntegrationTest {

    private final FixNodeTransactionsMigration migration;
    private final NodeRepository nodeRepository;
    private final DomainBuilder domainBuilder;
    private final RecordItemBuilder recordItemBuilder;

    @Test
    void empty() {
        softly.assertThat(nodeRepository.count()).isZero();
        runMigration();
        softly.assertThat(nodeRepository.count()).isZero();
        softly.assertThat(findHistory(Node.class)).isEmpty();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void migrateNoHistory(CapturedOutput output) {
        var expectedNodes = persist();

        softly.assertThat(nodeRepository.count()).isZero();

        runMigration();

        softly.assertThat(nodeRepository.count()).isEqualTo(3);
        softly.assertThat(nodeRepository.findAll()).containsExactlyInAnyOrderElementsOf(expectedNodes);
        verifyFailedItem(output, expectedNodes.getFirst().getCreatedTimestamp());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void migrateHistory(CapturedOutput output) {
        var originalNodes = persist();
        var historyNodes = new ArrayList<Node>();
        var newNodes = new ArrayList<Node>();

        for (Node node : originalNodes) {
            var nodeDelete = recordItemBuilder
                    .nodeDelete()
                    .transactionBody(tb -> tb.setNodeId(node.getNodeId()))
                    .build();

            var nodeUpdate = recordItemBuilder
                    .nodeUpdate()
                    .transactionBody(tb -> tb.setNodeId(node.getNodeId()))
                    .build();

            node.setTimestampRange(Range.closedOpen(node.getTimestampLower(), nodeDelete.getConsensusTimestamp()));
            historyNodes.add(node);
            historyNodes.add(Node.builder()
                    .adminKey(node.getAdminKey())
                    .createdTimestamp(node.getCreatedTimestamp())
                    .declineReward(false)
                    .deleted(true)
                    .nodeId(node.getNodeId())
                    .timestampRange(
                            Range.closedOpen(nodeDelete.getConsensusTimestamp(), nodeUpdate.getConsensusTimestamp()))
                    .build());

            newNodes.add(Node.builder()
                    .adminKey(nodeUpdate
                            .getTransactionBody()
                            .getNodeUpdate()
                            .getAdminKey()
                            .toByteArray())
                    .createdTimestamp(node.getCreatedTimestamp())
                    .declineReward(false)
                    .deleted(false)
                    .nodeId(node.getNodeId())
                    .timestampRange(Range.atLeast(nodeUpdate.getConsensusTimestamp()))
                    .build());

            domainBuilder
                    .transaction()
                    .customize(
                            t -> t.transactionBytes(nodeDelete.getTransaction().toByteArray())
                                    .consensusTimestamp(nodeDelete.getConsensusTimestamp())
                                    .transactionRecordBytes(
                                            nodeDelete.getTransactionRecord().toByteArray())
                                    .type(TransactionType.NODEDELETE.getProtoId()))
                    .persist();

            domainBuilder
                    .transaction()
                    .customize(
                            t -> t.transactionBytes(nodeUpdate.getTransaction().toByteArray())
                                    .consensusTimestamp(nodeUpdate.getConsensusTimestamp())
                                    .transactionRecordBytes(
                                            nodeUpdate.getTransactionRecord().toByteArray())
                                    .type(TransactionType.NODEUPDATE.getProtoId()))
                    .persist();
        }

        softly.assertThat(nodeRepository.count()).isZero();

        runMigration();

        softly.assertThat(nodeRepository.findAll()).containsExactlyInAnyOrderElementsOf(newNodes);
        softly.assertThat(findHistory(Node.class)).containsExactlyInAnyOrderElementsOf(historyNodes);

        verifyFailedItem(output, originalNodes.getFirst().getCreatedTimestamp());
    }

    private void verifyFailedItem(CapturedOutput output, Long createdTimestamp) {
        var failedRecordItemTs = createdTimestamp + 3;
        var failedStatus = ResponseCodeEnum.FAIL_INVALID;
        var expectedLog = "Skipping node transaction %d with status %s as node is not parsable"
                .formatted(failedRecordItemTs, failedStatus.name());

        softly.assertThat(output).contains(expectedLog);
    }

    @SneakyThrows
    private void runMigration() {
        migration.doMigrate();
    }

    private List<Node> persist() {
        var nodeCreateRecordItem = recordItemBuilder.nodeCreate().build();

        // Update with nonce 1
        var nodeUpdateRecordItem = recordItemBuilder
                .nodeUpdate()
                .record(r -> r.setTransactionID(
                                r.getTransactionID().toBuilder().setNonce(1).build())
                        .setConsensusTimestamp(TestUtils.toTimestamp(nodeCreateRecordItem.getConsensusTimestamp() + 1)))
                .build();

        var nodeUpdateWithoutCreateRecordItem = recordItemBuilder
                .nodeUpdate()
                .record(r -> r.setConsensusTimestamp(
                        TestUtils.toTimestamp(nodeCreateRecordItem.getConsensusTimestamp() + 2)))
                .build();

        var failedRecordItem = recordItemBuilder
                .nodeUpdate()
                .record(r -> r.setConsensusTimestamp(
                        TestUtils.toTimestamp(nodeCreateRecordItem.getConsensusTimestamp() + 3)))
                .receipt(r -> r.setStatus(ResponseCodeEnum.FAIL_INVALID))
                .build();

        domainBuilder
                .transaction()
                .customize(t -> t.transactionBytes(
                                nodeCreateRecordItem.getTransaction().toByteArray())
                        .consensusTimestamp(nodeCreateRecordItem.getConsensusTimestamp())
                        .transactionRecordBytes(
                                nodeCreateRecordItem.getTransactionRecord().toByteArray())
                        .type(TransactionType.NODECREATE.getProtoId()))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.transactionBytes(
                                nodeUpdateRecordItem.getTransaction().toByteArray())
                        .consensusTimestamp(nodeUpdateRecordItem.getConsensusTimestamp())
                        .transactionRecordBytes(
                                nodeUpdateRecordItem.getTransactionRecord().toByteArray())
                        .type(TransactionType.NODEUPDATE.getProtoId()))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.transactionBytes(nodeUpdateWithoutCreateRecordItem
                                .getTransaction()
                                .toByteArray())
                        .consensusTimestamp(nodeUpdateWithoutCreateRecordItem.getConsensusTimestamp())
                        .transactionRecordBytes(nodeUpdateWithoutCreateRecordItem
                                .getTransactionRecord()
                                .toByteArray())
                        .type(TransactionType.NODEUPDATE.getProtoId()))
                .persist();

        domainBuilder
                .transaction()
                .customize(t -> t.transactionBytes(
                                failedRecordItem.getTransaction().toByteArray())
                        .consensusTimestamp(failedRecordItem.getConsensusTimestamp())
                        .transactionRecordBytes(
                                failedRecordItem.getTransactionRecord().toByteArray())
                        .type(TransactionType.NODEUPDATE.getProtoId()))
                .persist();

        var expectedNodeCreate = Node.builder()
                .adminKey(nodeCreateRecordItem
                        .getTransactionBody()
                        .getNodeCreate()
                        .getAdminKey()
                        .toByteArray())
                .createdTimestamp(nodeCreateRecordItem.getConsensusTimestamp())
                .declineReward(false)
                .deleted(false)
                .nodeId(nodeCreateRecordItem.getTransactionRecord().getReceipt().getNodeId())
                .timestampRange(Range.atLeast(nodeCreateRecordItem.getConsensusTimestamp()))
                .build();

        var expectedNodeUpdate = Node.builder()
                .adminKey(nodeUpdateRecordItem
                        .getTransactionBody()
                        .getNodeUpdate()
                        .getAdminKey()
                        .toByteArray())
                .createdTimestamp(nodeUpdateRecordItem.getConsensusTimestamp())
                .declineReward(false)
                .deleted(false)
                .nodeId(nodeUpdateRecordItem
                        .getTransactionBody()
                        .getNodeUpdate()
                        .getNodeId())
                .timestampRange(Range.atLeast(nodeUpdateRecordItem.getConsensusTimestamp()))
                .build();

        var expectedNodeUpdateWithoutCreate = Node.builder()
                .adminKey(nodeUpdateWithoutCreateRecordItem
                        .getTransactionBody()
                        .getNodeUpdate()
                        .getAdminKey()
                        .toByteArray())
                .createdTimestamp(null)
                .declineReward(false)
                .deleted(false)
                .nodeId(nodeUpdateWithoutCreateRecordItem
                        .getTransactionBody()
                        .getNodeUpdate()
                        .getNodeId())
                .timestampRange(Range.atLeast(nodeUpdateWithoutCreateRecordItem.getConsensusTimestamp()))
                .build();

        return List.of(expectedNodeCreate, expectedNodeUpdate, expectedNodeUpdateWithoutCreate);
    }
}
