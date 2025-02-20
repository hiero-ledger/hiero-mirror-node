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

@Named
final class ContractCallTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        if (recordBuilder.getContractCallResult().hasContractID()) {
            recordBuilder
                    .getReceiptBuilder()
                    .setContractID(recordBuilder.getContractCallResult().getContractID());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTCALL;
    }
}
