// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.ledger.Ledger;
import org.hiero.mirror.common.domain.ledger.NodeContribution;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
@RequiredArgsConstructor
final class LedgerIdPublicationTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final ImporterProperties importerProperties;

    @Override
    protected void doUpdateTransaction(final Transaction transaction, final RecordItem recordItem) {
        final var transactionBody = recordItem.getTransactionBody().getLedgerIdPublication();

        final List<NodeContribution> nodeContributions = new ArrayList<>();
        for (final var nodeContribution : transactionBody.getNodeContributionsList()) {
            nodeContributions.add(NodeContribution.builder()
                    .historyProofKey(DomainUtils.toBytes(nodeContribution.getHistoryProofKey()))
                    .nodeId(nodeContribution.getNodeId())
                    .weight(nodeContribution.getWeight())
                    .build());
        }

        final var ledger = Ledger.builder()
                .historyProofVerificationKey(DomainUtils.toBytes(transactionBody.getHistoryProofVerificationKey()))
                .ledgerId(DomainUtils.toBytes(transactionBody.getLedgerId()))
                .network(importerProperties.getNetwork())
                .nodeContributions(nodeContributions)
                .blockNumberRange(Range.atLeast(0L))
                .build();
        entityListener.onLedger(ledger);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.LEDGERIDPUBLICATION;
    }
}
