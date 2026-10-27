// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.hiero.mirror.common.domain.transaction.StateChangeContext;
import org.hiero.mirror.common.util.DomainUtils;

abstract class AbstractContractTransformer extends AbstractBlockTransactionTransformer {

    /**
     * Sets the receipt contract id from the transaction body, resolving an EVM address from its long-zero form or the
     * state changes on a best-effort basis. An unresolved EVM address is left for the parser to resolve.
     */
    void setReceiptContractId(
            final ContractID contractId,
            final TransactionReceipt.Builder receiptBuilder,
            final StateChangeContext stateChangeContext) {
        if (!contractId.hasEvmAddress()) {
            receiptBuilder.setContractID(contractId);
            return;
        }

        final var entityId = DomainUtils.fromEvmAddress(DomainUtils.toBytes(contractId.getEvmAddress()));
        if (entityId != null
                && entityId.getShard() == contractId.getShardNum()
                && entityId.getRealm() == contractId.getRealmNum()) {
            receiptBuilder.setContractID(contractId.toBuilder().setContractNum(entityId.getNum()));
            return;
        }

        final var resolved = stateChangeContext.getContractId(contractId.getEvmAddress());
        if (resolved != null) {
            receiptBuilder.setContractID(resolved);
        }
    }
}
