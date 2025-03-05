// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class CryptoCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        if (!blockItem.isSuccessful()) {
            return;
        }

        var recordBuilder = recordItemBuilder.transactionRecordBuilder();
        var alias = transactionBody.getCryptoCreateAccount().getAlias();
        if (alias.size() == DomainUtils.EVM_ADDRESS_LENGTH) {
            recordBuilder.setEvmAddress(alias);
        }

        var accountCreate =
                blockItem.getTransactionOutput(TransactionCase.ACCOUNT_CREATE).getAccountCreate();
        var receiptBuilder = recordBuilder.getReceiptBuilder();
        receiptBuilder.setAccountID(accountCreate.getCreatedAccountId());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOCREATEACCOUNT;
    }
}
