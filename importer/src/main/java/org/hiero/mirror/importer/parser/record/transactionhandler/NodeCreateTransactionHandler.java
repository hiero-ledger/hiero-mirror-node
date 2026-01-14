// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.node.Node;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
class NodeCreateTransactionHandler extends AbstractNodeTransactionHandler {

    private final EntityIdService entityIdService;

    public NodeCreateTransactionHandler(EntityListener entityListener, EntityIdService entityIdService) {
        super(entityListener);
        this.entityIdService = entityIdService;
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
        if (!recordItem.isSuccessful()) {
            return null;
        }

        var nodeCreate = recordItem.getTransactionBody().getNodeCreate();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var grpcProxyEndpoint = nodeCreate.hasGrpcProxyEndpoint()
                ? toServiceEndpoint(consensusTimestamp, nodeCreate.getGrpcProxyEndpoint())
                : null;
        var key = nodeCreate.hasAdminKey() ? nodeCreate.getAdminKey().toByteArray() : null;
        final var accountId = entityIdService.lookup(nodeCreate.getAccountId()).orElse(EntityId.EMPTY);

        return Node.builder()
                .accountId(accountId)
                .adminKey(key)
                .createdTimestamp(consensusTimestamp)
                .declineReward(nodeCreate.getDeclineReward())
                .deleted(false)
                .grpcProxyEndpoint(grpcProxyEndpoint)
                .nodeId(recordItem.getTransactionRecord().getReceipt().getNodeId())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
    }
}
