// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
final class CryptoCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {
        if (!blockItem.successful()) {
            return;
        }

        var alias = transactionBody.getCryptoCreateAccount().getAlias();
        if (alias.size() == DomainUtils.EVM_ADDRESS_LENGTH) {
            transactionRecordBuilder.setEvmAddress(alias);
        }

        var receiptBuilder = transactionRecordBuilder.getReceiptBuilder();
        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasAccountCreate()) {
                var output = transactionOutput.getAccountCreate();
                if (output.hasCreatedAccountId()) {
                    receiptBuilder.setAccountID(output.getCreatedAccountId());
                    return;
                }
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOCREATEACCOUNT;
    }
}
