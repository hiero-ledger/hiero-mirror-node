// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
class NodeDeleteTransactionHandler extends AbstractNodeTransactionHandler {

    public NodeDeleteTransactionHandler(EntityListener entityListener) {
        super(entityListener);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.NODEDELETE;
    }

    @Override
    public Node parseNode(RecordItem recordItem) {
        if (recordItem.isSuccessful()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            var body = recordItem.getTransactionBody().getNodeDelete();

            return Node.builder()
                    .deleted(true)
                    .nodeId(body.getNodeId())
                    .timestampRange(Range.atLeast(consensusTimestamp))
                    .build();
        }

        return null;
    }
}
