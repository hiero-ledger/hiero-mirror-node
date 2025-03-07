// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.Node;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import jakarta.inject.Named;

@Named
class NodeCreateTransactionHandler extends AbstractNodeTransactionHandler {

    public NodeCreateTransactionHandler(EntityListener entityListener) {
        super(entityListener);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getNodeCreate().getAccountId());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.NODECREATE;
    }

    public Node parseNode(RecordItem recordItem) {
        if (recordItem.isSuccessful()) {
            var nodeCreate = recordItem.getTransactionBody().getNodeCreate();
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            return Node.builder()
                    .adminKey(nodeCreate.getAdminKey().toByteArray())
                    .createdTimestamp(consensusTimestamp)
                    .deleted(false)
                    .nodeId(recordItem.getTransactionRecord().getReceipt().getNodeId())
                    .timestampRange(Range.atLeast(consensusTimestamp))
                    .build();
        }

        return null;
    }
}
