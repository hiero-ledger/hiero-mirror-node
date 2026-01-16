// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
class NodeUpdateTransactionHandler extends AbstractNodeTransactionHandler {

    private final EntityIdService entityIdService;

    public NodeUpdateTransactionHandler(EntityListener entityListener, EntityIdService entityIdService) {
        super(entityListener);
        this.entityIdService = entityIdService;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getNodeUpdate().getAccountId());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.NODEUPDATE;
    }

    @Override
    public Node parseNode(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return null;
        }

        final var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var node = new Node();

        if (nodeUpdate.hasAccountId()) {
            entityIdService
                    .lookup(nodeUpdate.getAccountId())
                    .filter(e -> !EntityId.isEmpty(e))
                    .ifPresent(node::setAccountId);
        }

        if (nodeUpdate.hasAdminKey()) {
            node.setAdminKey(nodeUpdate.getAdminKey().toByteArray());
        }

        if (nodeUpdate.hasDeclineReward()) {
            node.setDeclineReward(nodeUpdate.getDeclineReward().getValue());
        }

        if (nodeUpdate.hasGrpcProxyEndpoint()) {
            node.setGrpcProxyEndpoint(toServiceEndpoint(consensusTimestamp, nodeUpdate.getGrpcProxyEndpoint()));
        }

        // As a special case, nodes migrated state to mirror nodes via a NodeUpdate instead of a proper NodeCreate
        if (recordItem.getTransactionRecord().getTransactionID().getNonce() > 0) {
            node.setCreatedTimestamp(consensusTimestamp);
        }

        node.setDeleted(false);
        node.setNodeId(nodeUpdate.getNodeId());
        node.setTimestampRange(Range.atLeast(consensusTimestamp));
        return node;
    }
}
