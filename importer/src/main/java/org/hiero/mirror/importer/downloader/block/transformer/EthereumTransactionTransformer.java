// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hederahashgraph.api.proto.java.EvmTransactionResult;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class EthereumTransactionTransformer extends AbstractBlockTransactionTransformer {

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

    @Override
    protected boolean hasReceiptContractId(
            final BlockTransaction blockTransaction, final EvmTransactionResult evmTransactionResult) {
        return evmTransactionResult.getGasUsed() > 0;
    }
}
