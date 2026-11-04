// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.ContractID;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.domain.EntityIdService;

@Named
@RequiredArgsConstructor
class ContractCallTransactionHandler extends AbstractTransactionHandler {

    private final EntityIdService entityIdService;

    /**
     * First attempts to extract the contract ID from the receipt, which was populated in HAPI 0.23 for contract calls.
     * Otherwise, falls back to checking the transaction body which may contain an EVM address. In case of partial
     * mirror nodes, it's possible the database does not have the mapping for that EVM address in the body, hence the
     * need for prioritizing the receipt.
     *
     * @param recordItem to check
     * @return The contract ID associated with this contract call
     */
    @Override
    public EntityId getEntity(RecordItem recordItem) {
        ContractID contractIdBody =
                recordItem.getTransactionBody().getContractCall().getContractID();
        ContractID contractIdReceipt =
                recordItem.getTransactionRecord().getReceipt().getContractID();
        return entityIdService.lookup(contractIdReceipt, contractIdBody).orElse(EntityId.EMPTY);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTCALL;
    }

    @Override
    public void updateContractResult(ContractResult contractResult, RecordItem recordItem) {
        if (recordItem.getTransactionBody().hasContractCall()) {
            var contractCallTransactionBody = recordItem.getTransactionBody().getContractCall();
            contractResult.setAmount(contractCallTransactionBody.getAmount());
            contractResult.setFunctionParameters(
                    DomainUtils.toBytes(contractCallTransactionBody.getFunctionParameters()));
            contractResult.setGasLimit(contractCallTransactionBody.getGas());
        }
    }
}
