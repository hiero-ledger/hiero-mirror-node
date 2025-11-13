// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.HookExecutionCollector;
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

    private void addTokenHookCalls(
            List<TokenTransferList> tokenTransfersList, HookExecutionCollector hookExecutionCollector) {
        for (TokenTransferList transferList : tokenTransfersList) {
            addHookCalls(transferList.getTransfersList(), hookExecutionCollector);
            addNftHookCalls(transferList.getNftTransfersList(), hookExecutionCollector);
        }
    }

    private void addNftHookCalls(List<NftTransfer> nftTransfersList, HookExecutionCollector hookExecutionCollector) {
        for (NftTransfer nftTransfer : nftTransfersList) {
            if (nftTransfer.hasPreTxSenderAllowanceHook()) {
                final var senderId =
                        entityIdService.lookup(nftTransfer.getSenderAccountID()).orElse(EntityId.EMPTY);
                hookExecutionCollector.addAllowExecHook(nftTransfer.getPreTxSenderAllowanceHook(), senderId.getId());
            } else if (nftTransfer.hasPrePostTxSenderAllowanceHook()) {
                final var senderId =
                        entityIdService.lookup(nftTransfer.getSenderAccountID()).orElse(EntityId.EMPTY);
                hookExecutionCollector.addPrePostExecHook(
                        nftTransfer.getPrePostTxSenderAllowanceHook(), senderId.getId());
            }

            if (nftTransfer.hasPreTxReceiverAllowanceHook()) {
                final var receiverId = entityIdService
                        .lookup(nftTransfer.getReceiverAccountID())
                        .orElse(EntityId.EMPTY);
                hookExecutionCollector.addAllowExecHook(
                        nftTransfer.getPreTxReceiverAllowanceHook(), receiverId.getId());
            } else if (nftTransfer.hasPrePostTxReceiverAllowanceHook()) {
                final var receiverId = entityIdService
                        .lookup(nftTransfer.getReceiverAccountID())
                        .orElse(EntityId.EMPTY);
                hookExecutionCollector.addPrePostExecHook(
                        nftTransfer.getPrePostTxReceiverAllowanceHook(), receiverId.getId());
            }
        }
    }

    private void addHookCalls(List<AccountAmount> accountAmountsList, HookExecutionCollector hookExecutionCollector) {
        for (AccountAmount accountAmount : accountAmountsList) {
            if (accountAmount.hasPreTxAllowanceHook()) {
                final var accountId =
                        entityIdService.lookup(accountAmount.getAccountID()).orElse(EntityId.EMPTY);
                hookExecutionCollector.addAllowExecHook(accountAmount.getPreTxAllowanceHook(), accountId.getId());
            } else if (accountAmount.hasPrePostTxAllowanceHook()) {
                final var accountId =
                        entityIdService.lookup(accountAmount.getAccountID()).orElse(EntityId.EMPTY);
                hookExecutionCollector.addPrePostExecHook(accountAmount.getPrePostTxAllowanceHook(), accountId.getId());
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOTRANSFER;
    }
}
