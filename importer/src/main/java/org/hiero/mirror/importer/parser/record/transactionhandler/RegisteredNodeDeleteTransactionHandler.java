// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
final class RegisteredNodeDeleteTransactionHandler extends AbstractRegisteredNodeTransactionHandler {

    RegisteredNodeDeleteTransactionHandler(EntityListener entityListener) {
        super(entityListener);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.REGISTEREDNODEDELETE;
    }

    @Override
    public RegisteredNode parseRegisteredNode(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return null;
        }
        final var deleteBody = recordItem.getTransactionBody().getRegisteredNodeDelete();
        return RegisteredNode.builder()
                .deleted(true)
                .registeredNodeId(deleteBody.getRegisteredNodeId())
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();
    }
}
