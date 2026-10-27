// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.hiero.mirror.common.util.DomainUtils;

abstract class AbstractContractTransformer extends AbstractBlockTransactionTransformer {

    /**
     * Sets the receipt contract id from the transaction body, converting a long-zero EVM address to its contract num.
     * Any other EVM address is left for the parser to resolve, since the block stream doesn't map it to a contract id.
     */
    void setReceiptContractId(final ContractID contractId, final TransactionReceipt.Builder receiptBuilder) {
        if (!contractId.hasEvmAddress()) {
            receiptBuilder.setContractID(contractId);
            return;
        }

        final var entityId = DomainUtils.fromEvmAddress(DomainUtils.toBytes(contractId.getEvmAddress()));
        if (entityId != null
                && entityId.getShard() == contractId.getShardNum()
                && entityId.getRealm() == contractId.getRealmNum()) {
            receiptBuilder.setContractID(contractId.toBuilder().setContractNum(entityId.getNum()));
        }
    }
}
