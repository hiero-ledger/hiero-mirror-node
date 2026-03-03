// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import com.hederahashgraph.api.proto.java.AccountAmount;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.contractlog.SyntheticContractLogService;
import org.hiero.mirror.importer.parser.contractlog.TransferContractLog;

@Named
@RequiredArgsConstructor
final class TransferEventsGenerator {

    private final SyntheticContractLogService syntheticContractLogService;
    private static final Comparator<AccountAmount> ACCOUNT_AMOUNT_COMPARATOR = Comparator.<AccountAmount>comparingLong(
                    e -> Math.abs(e.getAmount()))
            .reversed()
            .thenComparing(e -> e.getAccountID().getShardNum())
            .thenComparing(e -> e.getAccountID().getRealmNum())
            .thenComparing(
                    e -> e.getAccountID().hasAccountNum() ? e.getAccountID().getAccountNum() : 0L);

    public void generate(RecordItem recordItem, EntityId tokenId, List<AccountAmount> tokenTransfers) {
        if (tokenTransfers.size() < 2) {
            return;
        }

        final var expectedInitialCapacity = tokenTransfers.size() / 2;
        final var senders = new ArrayList<AccountAmount>(expectedInitialCapacity);
        final var receivers = new ArrayList<AccountAmount>(expectedInitialCapacity);

        for (final var transfer : tokenTransfers) {
            if (transfer.getAmount() <= 0) {
                senders.add(transfer);
            } else {
                receivers.add(transfer);
            }
        }

        senders.sort(ACCOUNT_AMOUNT_COMPARATOR);
        receivers.sort(ACCOUNT_AMOUNT_COMPARATOR);

        createSyntheticTransferEvents(senders, receivers, recordItem, tokenId);
    }

    private void createSyntheticTransferEvents(
            List<AccountAmount> senders, List<AccountAmount> receivers, RecordItem recordItem, EntityId tokenId) {
        if (senders.isEmpty() || receivers.isEmpty()) {
            return;
        }

        int s = 0;
        int r = 0;

        AccountAmount sender = null;
        AccountAmount receiver = null;
        var senderRemainingAmount = 0L;
        var receiverRemainingAmount = 0L;
        var amountForSyntheticContractLog = 0L;

        while (s < senders.size() && r < receivers.size()) {
            if (sender == null) {
                sender = senders.get(s);
                senderRemainingAmount = Math.abs(sender.getAmount());
            }
            if (receiver == null) {
                receiver = receivers.get(r);
                receiverRemainingAmount = receiver.getAmount();
            }

            amountForSyntheticContractLog = Math.min(senderRemainingAmount, receiverRemainingAmount);

            syntheticContractLogService.create(new TransferContractLog(
                    recordItem,
                    tokenId,
                    EntityId.of(sender.getAccountID()),
                    EntityId.of(receiver.getAccountID()),
                    amountForSyntheticContractLog));
            senderRemainingAmount -= amountForSyntheticContractLog;
            receiverRemainingAmount -= amountForSyntheticContractLog;

            if (senderRemainingAmount == 0) {
                s++;
                sender = null;
            }

            if (receiverRemainingAmount == 0) {
                r++;
                receiver = null;
            }
        }
    }
}
