// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import static java.util.Objects.requireNonNull;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
final class ConsensusCreateTopicTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();
        receiptBuilder.setTopicID(requireNonNull(
                blockTransaction.getStateChangeContext().getNewTopicId(), "No new topic id in state changes"));
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSCREATETOPIC;
    }
}
