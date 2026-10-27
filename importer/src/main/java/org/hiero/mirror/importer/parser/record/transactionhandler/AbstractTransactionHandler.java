// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

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

    /**
     * Updates the token's total supply from the receipt, unless the block stream left it unknown because the
     * transactional unit didn't change it in net, in which case the persisted total supply is already correct.
     */
    protected static void updateTokenTotalSupply(
            final EntityListener entityListener, final RecordItem recordItem, final EntityId tokenId) {
        if (recordItem.isTokenTotalSupplyUnknown()) {
            return;
        }

        final var token = new Token();
        token.setTokenId(tokenId.getId());
        token.setTotalSupply(recordItem.getTransactionRecord().getReceipt().getNewTotalSupply());
        entityListener.onToken(token);
    }
}
