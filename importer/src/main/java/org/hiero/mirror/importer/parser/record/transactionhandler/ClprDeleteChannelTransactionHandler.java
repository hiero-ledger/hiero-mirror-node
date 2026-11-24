// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@CustomLog
@Named
@RequiredArgsConstructor
final class ClprDeleteChannelTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRDELETECHANNEL;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var clprContext = recordItem.getClprContext();
        final var entry = clprContext == null ? null : clprContext.getClprChannel();
        if (entry == null) {
            // TODO:// handle this
            // No Channel record exists: an admin sweep of a legacy pending ownership commitment from a
            // network that predates ClprCreatehannel. No such commitment can exist in this mirror node's data.
            log.info("Ignoring legacy ClprChannelPendingCommitment sweep at {}", consensusTimestamp);
            return;
        }

        final var builder = ClprStateChangeHandler.populateFromStateChange(entry);
        entityListener.onClprChannel(
                builder.timestampRange(Range.atLeast(consensusTimestamp)).build());
    }
}
