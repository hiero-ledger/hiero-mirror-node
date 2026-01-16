// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.hiero.mirror.common.domain.transaction.StateChangeContext;
import org.hiero.mirror.common.util.DomainUtils;

abstract class AbstractContractTransformer extends AbstractBlockTransactionTransformer {

    void resolveEvmAddress(
            ContractID contractId, TransactionReceipt.Builder receiptBuilder, StateChangeContext stateChangeContext) {
        if (!contractId.hasEvmAddress()) {
            receiptBuilder.setContractID(contractId);
            return;
        }

        var entityId = DomainUtils.fromEvmAddress(DomainUtils.toBytes(contractId.getEvmAddress()));
        if (entityId != null
                && entityId.getShard() == contractId.getShardNum()
                && entityId.getRealm() == contractId.getRealmNum()) {
            receiptBuilder.setContractID(contractId.toBuilder().setContractNum(entityId.getNum()));
            return;
        }

        stateChangeContext
                .getContractId(contractId.getEvmAddress())
                .map(receiptBuilder::setContractID)
                .orElseThrow();
    }
}
