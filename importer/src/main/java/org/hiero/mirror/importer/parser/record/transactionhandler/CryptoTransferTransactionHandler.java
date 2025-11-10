// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import jakarta.inject.Named;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.HookExecutionCollector;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;

@Named
class CryptoTransferTransactionHandler extends AbstractTransactionHandler {

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        final var transactionBody = recordItem.getTransactionBody().getCryptoTransfer();
        HookExecutionCollector hookExecutionCollector = HookExecutionCollector.create();
        addHookCalls(transactionBody.getTransfers().getAccountAmountsList(), hookExecutionCollector);
        addTokenHookCalls(transactionBody.getTokenTransfersList(), hookExecutionCollector);
        recordItem.setHookExecutionQueue(hookExecutionCollector.buildExecutionQueue());
        super.doUpdateTransaction(transaction, recordItem);
    }

    private void addTokenHookCalls(
            List<TokenTransferList> tokenTransfersList, HookExecutionCollector hookExecutionCollector) {
        for (TokenTransferList transferList : tokenTransfersList) {
            for (AccountAmount accountAmount : transferList.getTransfersList()) {
                addHookCalls(hookExecutionCollector, accountAmount);
            }
            addNftHookCalls(transferList.getNftTransfersList(), hookExecutionCollector);
        }
    }

    private void addHookCalls(HookExecutionCollector hookExecutionCollector, AccountAmount accountAmount) {
        final var accountId = EntityId.of(accountAmount.getAccountID());
        if (accountAmount.hasPreTxAllowanceHook()) {
            final var hookCall = accountAmount.getPreTxAllowanceHook();
            if (hookCall.hasHookId()) {
                hookExecutionCollector.addAllowExecHook(hookCall.getHookId(), accountId.getId());
            }
        } else if (accountAmount.hasPrePostTxAllowanceHook()) {
            final var hookCall = accountAmount.getPrePostTxAllowanceHook();
            hookExecutionCollector.addPrePostExecHook(hookCall.getHookId(), accountId.getId());
        }
    }

    private void addNftHookCalls(List<NftTransfer> nftTransfersList, HookExecutionCollector hookExecutionCollector) {
        for (NftTransfer nftTransfer : nftTransfersList) {
            var receiverId = EntityId.of(nftTransfer.getReceiverAccountID());
            var senderId = EntityId.of(nftTransfer.getSenderAccountID());
            if (nftTransfer.hasPreTxSenderAllowanceHook()) {
                final var hookCall = nftTransfer.getPreTxSenderAllowanceHook();
                if (hookCall.hasHookId()) {
                    hookExecutionCollector.addAllowExecHook(hookCall.getHookId(), senderId.getId());
                }
            } else if (nftTransfer.hasPrePostTxSenderAllowanceHook()) {
                final var hookCall = nftTransfer.getPrePostTxSenderAllowanceHook();
                if (hookCall.hasHookId()) {
                    hookExecutionCollector.addPrePostExecHook(hookCall.getHookId(), senderId.getId());
                }
            }

            if (nftTransfer.hasPreTxReceiverAllowanceHook()) {
                final var hookCall = nftTransfer.getPreTxReceiverAllowanceHook();
                if (hookCall.hasHookId()) {
                    hookExecutionCollector.addAllowExecHook(hookCall.getHookId(), receiverId.getId());
                }
            } else if (nftTransfer.hasPrePostTxReceiverAllowanceHook()) {
                final var hookCall = nftTransfer.getPrePostTxReceiverAllowanceHook();
                if (hookCall.hasHookId()) {
                    hookExecutionCollector.addPrePostExecHook(hookCall.getHookId(), receiverId.getId());
                }
            }
        }
    }

    private void addHookCalls(List<AccountAmount> accountAmountsList, HookExecutionCollector hookExecutionCollector) {
        for (AccountAmount accountAmount : accountAmountsList) {
            addHookCalls(hookExecutionCollector, accountAmount);
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOTRANSFER;
    }
}
