// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
final class RegisteredNodeUpdateTransactionHandler extends AbstractRegisteredNodeTransactionHandler {

    RegisteredNodeUpdateTransactionHandler(EntityListener entityListener) {
        super(entityListener);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.EMPTY;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.REGISTEREDNODEUPDATE;
    }

    @Override
    public RegisteredNode parseRegisteredNode(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return null;
        }

        final var nodeUpdate = recordItem.getTransactionBody().getRegisteredNodeUpdate();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var node = new RegisteredNode();

        if (nodeUpdate.hasAdminKey()) {
            node.setAdminKey(nodeUpdate.getAdminKey().toByteArray());
        }

        if (nodeUpdate.hasDescription()) {
            node.setDescription(nodeUpdate.getDescription().getValue());
        }

        if (!nodeUpdate.getServiceEndpointList().isEmpty()) {
            node.setServiceEndpoints(nodeUpdate.getServiceEndpointList().stream()
                    .map(this::toRegisteredServiceEndpoint)
                    .toList());
        }

        node.setDeleted(false);
        node.setRegisteredNodeId(nodeUpdate.getRegisteredNodeId());
        node.setTimestampRange(Range.atLeast(consensusTimestamp));
        return node;
    }
}
