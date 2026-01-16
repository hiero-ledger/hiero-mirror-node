// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.ContractID;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
class ContractDeleteTransactionHandler extends AbstractEntityCrudTransactionHandler {

    ContractDeleteTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.CONTRACTDELETEINSTANCE);
    }

    /**
     * First attempts to extract the contract ID from the receipt, which was populated in HAPI 0.23 for contract
     * deletes. Otherwise, falls back to checking the transaction body which may contain an EVM address. In case of
     * partial mirror nodes, it's possible the database does not have the mapping for that EVM address in the body,
     * hence the need for prioritizing the receipt.
     *
     * @param recordItem to check
     * @return The contract ID associated with this contract delete
     */
    @Override
    public EntityId getEntity(RecordItem recordItem) {
        ContractID contractIdBody =
                recordItem.getTransactionBody().getContractDeleteInstance().getContractID();
        ContractID contractIdReceipt =
                recordItem.getTransactionRecord().getReceipt().getContractID();
        return entityIdService.lookup(contractIdReceipt, contractIdBody).orElse(EntityId.EMPTY);
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractDeleteInstance();
        EntityId obtainerId = null;

        if (transactionBody.hasTransferAccountID()) {
            obtainerId = EntityId.of(transactionBody.getTransferAccountID());
        } else if (transactionBody.hasTransferContractID()) {
            obtainerId = entityIdService
                    .lookup(transactionBody.getTransferContractID())
                    .orElse(EntityId.EMPTY);
        }

        entity.setObtainerId(obtainerId);
        entity.setPermanentRemoval(transactionBody.getPermanentRemoval());
        entity.setType(EntityType.CONTRACT);
        entityListener.onEntity(entity);
        recordItem.addEntityId(obtainerId);
    }
}
