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

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem.RecordItemBuilder;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
final class EthereumTransactionTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem,
            RecordItemBuilder recordItemBuilder,
            StateChangeContext stateChangeContext,
            TransactionBody transactionBody) {
        if (!blockItem.transactionOutputs().containsKey(TransactionCase.ETHEREUM_CALL)) {
            return;
        }

        var ethereumCall = blockItem
                .transactionOutputs()
                .get(TransactionCase.ETHEREUM_CALL)
                .getEthereumCall();
        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        recordBuilder.setEthereumHash(ethereumCall.getEthereumHash());
        recordItemBuilder.sidecarRecords(ethereumCall.getSidecarsList());

        switch (ethereumCall.getEthResultCase()) {
            case ETHEREUM_CALL_RESULT -> recordBuilder.setContractCallResult(ethereumCall.getEthereumCallResult());
            case ETHEREUM_CREATE_RESULT -> recordBuilder.setContractCreateResult(
                    ethereumCall.getEthereumCreateResult());
            default -> log.warn(
                    "Unhandled eth_result case {} for transaction at {}",
                    ethereumCall.getEthResultCase(),
                    DomainUtils.timestampInNanosMax(
                            blockItem.transactionResult().getConsensusTimestamp()));
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }
}
