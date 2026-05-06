// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.springframework.context.ApplicationEventPublisher;

@Named
final class RegisteredNodeCreateTransactionHandler extends AbstractRegisteredNodeTransactionHandler {

    RegisteredNodeCreateTransactionHandler(
            final ApplicationEventPublisher applicationEventPublisher, final EntityListener entityListener) {
        super(applicationEventPublisher, entityListener);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.REGISTEREDNODECREATE;
    }

    @Override
    public RegisteredNode parseRegisteredNode(final RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return null;
        }

        final var txnBody = recordItem.getTransactionBody();
        if (!txnBody.hasRegisteredNodeCreate()) {
            return null;
        }

        final var nodeCreate = txnBody.getRegisteredNodeCreate();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final long registeredNodeId =
                recordItem.getTransactionRecord().getReceipt().getRegisteredNodeId();

        final var adminKey = nodeCreate.hasAdminKey() ? nodeCreate.getAdminKey().toByteArray() : null;
        final var builder = RegisteredNode.builder();
        parseServiceEndpoints(builder, nodeCreate.getServiceEndpointList());

        return builder.adminKey(adminKey)
                .createdTimestamp(consensusTimestamp)
                .deleted(false)
                .description(nodeCreate.getDescription())
                .registeredNodeId(registeredNodeId)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
    }
}
