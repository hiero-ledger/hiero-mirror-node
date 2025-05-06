// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.Node;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
class NodeUpdateTransactionHandler extends AbstractNodeTransactionHandler {

    public NodeUpdateTransactionHandler(EntityListener entityListener) {
        super(entityListener);
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

        var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var node = new Node();

        if (nodeUpdate.hasAdminKey()) {
            node.setAdminKey(nodeUpdate.getAdminKey().toByteArray());
        }

        if (nodeUpdate.hasDeclineReward()) {
            node.setDeclineReward(nodeUpdate.getDeclineReward().getValue());
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
