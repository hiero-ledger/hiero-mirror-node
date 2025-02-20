/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem.RecordItemBuilder;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
final class ContractUpdateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem,
            RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TransactionBody transactionBody) {
        if (!blockItem.successful()) {
            return;
        }

        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        var contractId = transactionBody.getContractUpdateInstance().getContractID();
        if (!contractId.hasEvmAddress()) {
            receiptBuilder.setContractID(contractId);
            return;
        }

        // TODO, long form contract id
        stateChangeContext
                .getContractId(contractId.getEvmAddress())
                .ifPresentOrElse(
                        receiptBuilder::setContractID,
                        () -> log.warn(
                                "No contract id mapping from evm address found for ContractDelete transaction at {}",
                                blockItem.consensusTimestamp()));
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTUPDATEINSTANCE;
    }
}
