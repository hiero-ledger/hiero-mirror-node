// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.StateChangeContext;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;

abstract class AbstractContractTransformer extends AbstractBlockItemTransformer {

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
