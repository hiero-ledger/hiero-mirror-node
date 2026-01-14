// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.domain.EntityIdService;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
class CryptoTransferTransactionHandler extends AbstractTransactionHandler {

    private final EntityIdService entityIdService;

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        final var transactionBody = recordItem.getTransactionBody().getCryptoTransfer();
        final var hookExecutionCollector = HookExecutionCollector.create();
        addHookCalls(transactionBody.getTransfers().getAccountAmountsList(), hookExecutionCollector);
        addTokenHookCalls(transactionBody.getTokenTransfersList(), hookExecutionCollector);
        recordItem.setHookExecutionQueue(hookExecutionCollector.buildExecutionQueue());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOTRANSFER;
    }

    private void addHookCalls(
            final List<AccountAmount> accountAmountsList, final HookExecutionCollector hookExecutionCollector) {
        for (final var accountAmount : accountAmountsList) {
            final var accountId = accountAmount.getAccountID();
            if (accountAmount.hasPreTxAllowanceHook()) {
                hookExecutionCollector.addAllowExecHook(accountAmount.getPreTxAllowanceHook(), lookup(accountId));
            } else if (accountAmount.hasPrePostTxAllowanceHook()) {
                hookExecutionCollector.addPrePostExecHook(accountAmount.getPrePostTxAllowanceHook(), lookup(accountId));
            }
        }
    }

    private void addNftHookCalls(
            final List<NftTransfer> nftTransfersList, final HookExecutionCollector hookExecutionCollector) {
        for (final var nftTransfer : nftTransfersList) {
            // sender hook is executed first
            final var senderAccountId = nftTransfer.getSenderAccountID();
            if (nftTransfer.hasPreTxSenderAllowanceHook()) {
                hookExecutionCollector.addAllowExecHook(
                        nftTransfer.getPreTxSenderAllowanceHook(), lookup(senderAccountId));
            } else if (nftTransfer.hasPrePostTxSenderAllowanceHook()) {
                hookExecutionCollector.addPrePostExecHook(
                        nftTransfer.getPrePostTxSenderAllowanceHook(), lookup(senderAccountId));
            }

            final var receiverAccountId = nftTransfer.getReceiverAccountID();
            if (nftTransfer.hasPreTxReceiverAllowanceHook()) {
                hookExecutionCollector.addAllowExecHook(
                        nftTransfer.getPreTxReceiverAllowanceHook(), lookup(receiverAccountId));
            } else if (nftTransfer.hasPrePostTxReceiverAllowanceHook()) {
                hookExecutionCollector.addPrePostExecHook(
                        nftTransfer.getPrePostTxReceiverAllowanceHook(), lookup(receiverAccountId));
            }
        }
    }

    private void addTokenHookCalls(
            final List<TokenTransferList> tokenTransfersList, final HookExecutionCollector hookExecutionCollector) {
        for (final var transferList : tokenTransfersList) {
            addHookCalls(transferList.getTransfersList(), hookExecutionCollector);
            addNftHookCalls(transferList.getNftTransfersList(), hookExecutionCollector);
        }
    }

    private long lookup(final AccountID accountId) {
        return entityIdService.lookup(accountId).orElse(EntityId.EMPTY).getId();
    }
}
