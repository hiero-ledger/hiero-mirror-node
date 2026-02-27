// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
final class RegisteredNodeUpdateTransactionHandler extends AbstractRegisteredNodeTransactionHandler {

    RegisteredNodeUpdateTransactionHandler(EntityListener entityListener) {
        super(entityListener);
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
            final var endpointList = nodeUpdate.getServiceEndpointList();
            final int size = endpointList.size();
            final List<RegisteredServiceEndpoint> serviceEndpoints = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                serviceEndpoints.add(toRegisteredServiceEndpoint(endpointList.get(i)));
            }
            node.setServiceEndpoints(serviceEndpoints);
        }

        node.setDeleted(false);
        node.setRegisteredNodeId(nodeUpdate.getRegisteredNodeId());
        node.setTimestampRange(Range.atLeast(consensusTimestamp));
        return node;
    }
}
