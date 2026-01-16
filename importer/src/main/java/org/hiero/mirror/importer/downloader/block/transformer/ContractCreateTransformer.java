// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.BlockTransaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class ContractCreateTransformer extends AbstractBlockTransactionTransformer {

    @Override
    public TransactionType getType() {
        return TransactionType.CONTRACTCREATEINSTANCE;
    }

    @Override
    protected EvmTransactionInfo getEvmTransactionInfo(BlockTransaction blockTransaction) {
        return blockTransaction
                .getTransactionOutput(TransactionCase.CONTRACT_CREATE)
                .map(TransactionOutput::getContractCreate)
                .map(createContractOutput -> {
                    if (!createContractOutput.hasEvmTransactionResult()) {
                        log.warn(
                                "CreateContractOutput has no EvmTransactionResult at {}",
                                blockTransaction.getConsensusTimestamp());
                        return null;
                    }

                    return EvmTransactionInfo.ofContractCreate(createContractOutput.getEvmTransactionResult());
                })
                .orElse(null);
    }
}
