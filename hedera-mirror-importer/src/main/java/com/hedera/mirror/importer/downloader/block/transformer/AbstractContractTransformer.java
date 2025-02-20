// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractContractTransformer extends AbstractBlockItemTransformer {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    void resolveEvmAddress(
            ContractID contractId,
            long consensusTimestamp,
            TransactionReceipt.Builder receiptBuilder,
            StateChangeContext stateChangeContext) {
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
                .ifPresentOrElse(
                        receiptBuilder::setContractID,
                        () -> log.warn(
                                "No contract id mapping from evm address found for {} transaction at {}",
                                getType(),
                                consensusTimestamp));
    }
}
