// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.google.protobuf.Int64Value;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class EthereumTransactionTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var recordBuilder = blockTransactionTransformation.recordItemBuilder().transactionRecordBuilder();
        if (!recordBuilder.hasContractCallResult() && !recordBuilder.hasContractCreateResult()) {
            return;
        }

        var resultBuilder = recordBuilder.hasContractCallResult()
                ? recordBuilder.getContractCallResultBuilder()
                : recordBuilder.getContractCreateResultBuilder();
        if (resultBuilder.getGasUsed() == 0) {
            recordBuilder.getReceiptBuilder().clearContractID();
        }

        var blockTransaction = blockTransactionTransformation.blockTransaction();
        blockTransaction
                .getStateChangeContext()
                .getAccount(resultBuilder.getSenderId())
                .ifPresent(account -> resultBuilder.setSignerNonce(Int64Value.of(account.getEthereumNonce())));
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }

    @Override
    protected EvmTransactionInfo getEvmTransactionInfo(BlockTransaction blockTransaction) {
        var ethereumOutput = blockTransaction
                .getTransactionOutput(TransactionCase.ETHEREUM_CALL)
                .map(TransactionOutput::getEthereumCall)
                .orElse(null);
        if (ethereumOutput == null) {
            return null;
        }

        var transactionResultCase = ethereumOutput.getTransactionResultCase();
        return switch (transactionResultCase) {
            case EVM_CALL_TRANSACTION_RESULT ->
                EvmTransactionInfo.ofContractCall(ethereumOutput.getEvmCallTransactionResult());
            case EVM_CREATE_TRANSACTION_RESULT ->
                EvmTransactionInfo.ofContractCreate(ethereumOutput.getEvmCreateTransactionResult());
            default -> {
                log.warn(
                        "Unhandled transaction result case {} in EthereumOutput at {}",
                        transactionResultCase,
                        blockTransaction.getConsensusTimestamp());
                yield null;
            }
        };
    }
}
