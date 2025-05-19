// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractTransactionHandler implements TransactionHandler {

    @Override
    public final void updateTransaction(Transaction transaction, RecordItem recordItem) {
        addCommonEntityIds(transaction, recordItem);
        doUpdateTransaction(transaction, recordItem);
        updateEntity(transaction, recordItem);
    }

    protected void addCommonEntityIds(Transaction transaction, RecordItem recordItem) {
        recordItem.addEntityId(transaction.getEntityId());
        recordItem.addEntityId(transaction.getNodeAccountId());
        recordItem.addEntityId(transaction.getPayerAccountId());
    }

    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {}

    protected void updateEntity(Transaction transaction, RecordItem recordItem) {}
}
