// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
final class RegisteredNodeCreateTransactionHandler extends AbstractRegisteredNodeTransactionHandler {

    RegisteredNodeCreateTransactionHandler(EntityListener entityListener) {
        super(entityListener);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.REGISTEREDNODECREATE;
    }

    @Override
    public RegisteredNode parseRegisteredNode(RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return null;
        }

        final var txnBody = recordItem.getTransactionBody();
        if (!txnBody.hasRegisteredNodeCreate()) {
            return null;
        }

        final var nodeCreate = txnBody.getRegisteredNodeCreate();
        final var receipt = recordItem.getTransactionRecord().getReceipt();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();

        final var adminKey = nodeCreate.hasAdminKey() ? nodeCreate.getAdminKey().toByteArray() : null;
        final var description = nodeCreate.getDescription();

        final var endpointList = nodeCreate.getServiceEndpointList();
        final int size = endpointList.size();
        final List<RegisteredServiceEndpoint> serviceEndpoints = new ArrayList<>(size);
        final var types = new TreeSet<Short>();
        for (int i = 0; i < size; i++) {
            final var endpoint = toRegisteredServiceEndpoint(endpointList.get(i));
            serviceEndpoints.add(endpoint);
            for (final var type : endpoint.getTypes()) {
                types.add(type.getValue());
            }
        }

        return RegisteredNode.builder()
                .adminKey(adminKey)
                .createdTimestamp(consensusTimestamp)
                .deleted(false)
                .description(description)
                .registeredNodeId(receipt.getRegisteredNodeId())
                .serviceEndpoints(serviceEndpoints)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .type(List.copyOf(types))
                .build();
    }
}
