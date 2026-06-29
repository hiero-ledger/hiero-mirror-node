// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.AccountID;
import jakarta.inject.Named;
import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.StateChangeContext;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
final class CryptoUpdateTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        final var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        final var transactionBody = blockTransaction.getTransactionBody().getCryptoUpdateAccount();
        final var accountIdToUpdate = transactionBody.getAccountIDToUpdate();
        final var stateChangeContext = blockTransaction.getStateChangeContext();

        final var receiptBuilder = blockTransactionTransformation
                .recordItemBuilder()
                .transactionRecordBuilder()
                .getReceiptBuilder();

        final var resolvedAccountId = resolveAccountId(stateChangeContext, accountIdToUpdate);
        if (resolvedAccountId.isPresent()) {
            receiptBuilder.setAccountID(resolvedAccountId.get());
        } else if (accountIdToUpdate.hasAccountNum()) {
            receiptBuilder.setAccountID(accountIdToUpdate);
        }

        if (!transactionBody.getDelegationAddress().isEmpty()) {
            resolvedAccountId
                    .flatMap(stateChangeContext::getAccount)
                    .ifPresent(account -> blockTransactionTransformation
                            .recordItemBuilder()
                            .accountEthereumNonce(account.getEthereumNonce()));
        }
    }

    private Optional<AccountID> resolveAccountId(StateChangeContext stateChangeContext, AccountID accountIdToUpdate) {
        if (accountIdToUpdate.hasAccountNum()) {
            return Optional.of(accountIdToUpdate);
        } else if (accountIdToUpdate.hasAlias()) {
            return stateChangeContext.getAccountId(accountIdToUpdate.getAlias());
        }

        return Optional.empty();
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOUPDATEACCOUNT;
    }
}
