// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;

@Named
final class FileCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void doTransform(
            BlockItem blockItem, RecordItem.RecordItemBuilder recordItemBuilder, TransactionBody transactionBody) {
        if (!blockItem.isSuccessful()) {
            return;
        }

        var receiptBuilder = recordItemBuilder.transactionRecordBuilder().getReceiptBuilder();
        blockItem
                .getStateChangeContext()
                .getNewFileId()
                .ifPresentOrElse(
                        receiptBuilder::setFileID,
                        () -> log.warn(
                                "No new file id found for FileCreate transaction at {}",
                                blockItem.getConsensusTimestamp()));
    }

    @Override
    public TransactionType getType() {
        return TransactionType.FILECREATE;
    }
}
