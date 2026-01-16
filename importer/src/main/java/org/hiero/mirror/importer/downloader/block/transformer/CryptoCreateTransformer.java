// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import jakarta.inject.Named;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.util.Utility;

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
        if (!alias.isEmpty() && alias.size() != DomainUtils.EVM_ADDRESS_LENGTH) {
            // This must be a synthetic transaction. The statechanges for its alias and evm address are in the top level
            // triggering crypto transfer transaction. However, there's no parent link to follow for statechanges
            // lookup. Instead, the evm address can be calculated from the alias.
            byte[] evmAddress = Utility.aliasToEvmAddress(DomainUtils.toBytes(alias));
            if (ArrayUtils.isNotEmpty(evmAddress)) {
                recordBuilder.setEvmAddress(DomainUtils.fromBytes(evmAddress));
            }
        }

        var accountCreate = blockTransaction
                .getTransactionOutput(TransactionCase.ACCOUNT_CREATE)
                .map(TransactionOutput::getAccountCreate)
                .orElseThrow();
        recordBuilder.getReceiptBuilder().setAccountID(accountCreate.getCreatedAccountId());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOCREATEACCOUNT;
    }
}
