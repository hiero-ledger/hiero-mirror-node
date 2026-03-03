// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import com.google.common.collect.Range;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.importer.repository.RegisteredNodeRepository;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class EntityRecordItemListenerRegisteredNodeTest extends AbstractEntityRecordItemListenerTest {

    private final RegisteredNodeRepository registeredNodeRepository;

    @Test
    void registeredNodeCreate() {
        final var recordItem = recordItemBuilder.registeredNodeCreate().build();
        final var nodeCreate = recordItem.getTransactionBody().getRegisteredNodeCreate();
        final var receipt = recordItem.getTransactionRecord().getReceipt();

        parseRecordItemAndCommit(recordItem);

        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(transactionRepository.findAll()).hasSize(1);
        softly.assertThat(registeredNodeRepository.findAll()).hasSize(1).first().satisfies(node -> {
            softly.assertThat(node.getRegisteredNodeId()).isEqualTo(receipt.getRegisteredNodeId());
            softly.assertThat(node.getCreatedTimestamp()).isEqualTo(recordItem.getConsensusTimestamp());
            softly.assertThat(node.isDeleted()).isFalse();
            softly.assertThat(node.getAdminKey())
                    .isEqualTo(nodeCreate.getAdminKey().toByteArray());
            softly.assertThat(node.getDescription()).isEqualTo(nodeCreate.getDescription());
            softly.assertThat(node.getServiceEndpoints())
                    .hasSize(nodeCreate.getServiceEndpointCount())
                    .allMatch(e -> e.getPort() > 0);
            softly.assertThat(node.getTimestampRange().lowerEndpoint()).isEqualTo(recordItem.getConsensusTimestamp());
        });
        softly.assertThat(findHistory(RegisteredNode.class)).isEmpty();
    }

    @Test
    void registeredNodeUpdate() {
        final var registeredNode = domainBuilder.registeredNode().persist();
        final var recordItem = recordItemBuilder
                .registeredNodeUpdate()
                .transactionBody(b -> b.setRegisteredNodeId(registeredNode.getRegisteredNodeId()))
                .build();
        final var nodeUpdate = recordItem.getTransactionBody().getRegisteredNodeUpdate();

        parseRecordItemAndCommit(recordItem);

        registeredNode.setTimestampUpper(recordItem.getConsensusTimestamp());

        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(registeredNodeRepository.findAll()).hasSize(1).first().satisfies(node -> {
            softly.assertThat(node.getRegisteredNodeId()).isEqualTo(registeredNode.getRegisteredNodeId());
            softly.assertThat(node.getCreatedTimestamp()).isEqualTo(registeredNode.getCreatedTimestamp());
            softly.assertThat(node.isDeleted()).isFalse();
            softly.assertThat(node.getAdminKey())
                    .isEqualTo(nodeUpdate.getAdminKey().toByteArray());
            softly.assertThat(node.getDescription())
                    .isEqualTo(nodeUpdate.getDescription().getValue());
            softly.assertThat(node.getServiceEndpoints()).hasSize(nodeUpdate.getServiceEndpointCount());
            softly.assertThat(node.getTimestampRange().lowerEndpoint()).isEqualTo(recordItem.getConsensusTimestamp());
        });
        softly.assertThat(findHistory(RegisteredNode.class)).containsExactly(registeredNode);
    }

    @Test
    void registeredNodeDelete() {
        final var registeredNode = domainBuilder.registeredNode().persist();
        final var recordItem = recordItemBuilder
                .registeredNodeDelete()
                .transactionBody(b -> b.setRegisteredNodeId(registeredNode.getRegisteredNodeId()))
                .build();
        final var deletedNode = registeredNode.toBuilder()
                .deleted(true)
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();

        parseRecordItemAndCommit(recordItem);

        registeredNode.setTimestampUpper(recordItem.getConsensusTimestamp());

        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(transactionRepository.findAll()).hasSize(1);
        softly.assertThat(registeredNodeRepository.findAll()).containsExactly(deletedNode);
        softly.assertThat(findHistory(RegisteredNode.class)).containsExactly(registeredNode);
    }
}
