// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;

@Named
final class CryptoCreateTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var recordBuilder = blockTransactionTransformation.recordItemBuilder().transactionRecordBuilder();
        var alias = blockTransactionTransformation
                .getTransactionBody()
                .getCryptoCreateAccount()
                .getAlias();
        if (alias.size() == DomainUtils.EVM_ADDRESS_LENGTH) {
            recordBuilder.setEvmAddress(alias);
        }

        var accountCreate = blockTransaction
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
