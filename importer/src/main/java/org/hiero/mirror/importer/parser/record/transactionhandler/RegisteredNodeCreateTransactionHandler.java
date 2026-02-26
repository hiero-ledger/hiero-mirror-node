// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
final class RegisteredNodeCreateTransactionHandler extends AbstractRegisteredNodeTransactionHandler {

    private final EntityIdService entityIdService;

    RegisteredNodeCreateTransactionHandler(EntityListener entityListener, EntityIdService entityIdService) {
        super(entityListener);
        this.entityIdService = entityIdService;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.EMPTY;
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
        EntityId nodeAccount = null;

        if (nodeCreate.hasNodeAccount()) {
            nodeAccount = entityIdService
                    .lookup(nodeCreate.getNodeAccount())
                    .filter(e -> !EntityId.isEmpty(e))
                    .orElse(null);
        }
        final var adminKey = nodeCreate.hasAdminKey() ? nodeCreate.getAdminKey().toByteArray() : null;
        final var description =
                StringUtils.isNotBlank(nodeCreate.getDescription()) ? nodeCreate.getDescription() : null;

        final var serviceEndpoints = nodeCreate.getServiceEndpointList().stream()
                .map(this::toRegisteredServiceEndpoint)
                .toList();

        return RegisteredNode.builder()
                .adminKey(adminKey)
                .createdTimestamp(consensusTimestamp)
                .deleted(false)
                .description(description)
                .nodeAccount(nodeAccount)
                .registeredNodeId(receipt.getRegisteredNodeId())
                .serviceEndpoints(serviceEndpoints)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
    }
}
