// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
@RequiredArgsConstructor
class FileAppendTransactionHandler extends AbstractTransactionHandler {

    private final FileDataHandler fileDataHandler;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getFileAppend().getFileID());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.FILEAPPEND;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        var contents = recordItem.getTransactionBody().getFileAppend().getContents();
        fileDataHandler.handle(transaction, contents);
    }
}
