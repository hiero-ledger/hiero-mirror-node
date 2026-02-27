// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
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
        final var description =
                StringUtils.isNotBlank(nodeCreate.getDescription()) ? nodeCreate.getDescription() : null;

        final var endpointList = nodeCreate.getServiceEndpointList();
        final int size = endpointList.size();
        final List<RegisteredServiceEndpoint> serviceEndpoints = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            serviceEndpoints.add(toRegisteredServiceEndpoint(endpointList.get(i)));
        }

        return RegisteredNode.builder()
                .adminKey(adminKey)
                .createdTimestamp(consensusTimestamp)
                .deleted(false)
                .description(description)
                .registeredNodeId(receipt.getRegisteredNodeId())
                .serviceEndpoints(serviceEndpoints)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
    }
}
