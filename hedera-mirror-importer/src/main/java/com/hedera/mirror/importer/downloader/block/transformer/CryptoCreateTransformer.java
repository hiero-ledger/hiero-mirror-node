// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import jakarta.inject.Named;

@Named
final class CryptoCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(BlockItemTransformation blockItemTransformation) {
        var blockItem = blockItemTransformation.blockItem();
        if (!blockItem.isSuccessful()) {
            return;
        }

        var recordBuilder = blockItemTransformation.recordItemBuilder().transactionRecordBuilder();
        var alias = blockItemTransformation
                .transactionBody()
                .getCryptoCreateAccount()
                .getAlias();
        if (alias.size() == DomainUtils.EVM_ADDRESS_LENGTH) {
            recordBuilder.setEvmAddress(alias);
        }

        var accountCreate = blockItem
                .getTransactionOutput(TransactionCase.ACCOUNT_CREATE)
                .map(TransactionOutput::getAccountCreate)
                .orElseThrow();
        var receiptBuilder = recordBuilder.getReceiptBuilder();
        receiptBuilder.setAccountID(accountCreate.getCreatedAccountId());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOCREATEACCOUNT;
    }
}
