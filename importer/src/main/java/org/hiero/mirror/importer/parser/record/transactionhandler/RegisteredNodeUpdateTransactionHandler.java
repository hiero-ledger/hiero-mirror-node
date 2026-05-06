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
final class RegisteredNodeUpdateTransactionHandler extends AbstractRegisteredNodeTransactionHandler {

    RegisteredNodeUpdateTransactionHandler(
            ApplicationEventPublisher applicationEventPublisher, EntityListener entityListener) {
        super(applicationEventPublisher, entityListener);
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
        final var builder = RegisteredNode.builder();

        if (nodeUpdate.hasAdminKey()) {
            builder.adminKey(nodeUpdate.getAdminKey().toByteArray());
        }

        if (nodeUpdate.hasDescription()) {
            builder.description(nodeUpdate.getDescription().getValue());
        }

        final var protoServiceEndpoints = nodeUpdate.getServiceEndpointList();
        if (!protoServiceEndpoints.isEmpty()) {
            parseServiceEndpoints(builder, protoServiceEndpoints);
        }

        return builder.deleted(false)
                .registeredNodeId(nodeUpdate.getRegisteredNodeId())
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();
    }
}
