// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.ledger.Ledger;
import org.hiero.mirror.common.domain.ledger.NodeContribution;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
public final class LedgerIdPublicationTransactionParser {

    private final ImporterProperties importerProperties;

    public Ledger parse(final long consensusTimestamp, final LedgerIdPublicationTransactionBody transactionBody) {
        final List<NodeContribution> nodeContributions = new ArrayList<>();
        for (final var nodeContribution : transactionBody.getNodeContributionsList()) {
            nodeContributions.add(NodeContribution.builder()
                    .historyProofKey(DomainUtils.toBytes(nodeContribution.getHistoryProofKey()))
                    .nodeId(nodeContribution.getNodeId())
                    .weight(nodeContribution.getWeight())
                    .build());
        }

        return Ledger.builder()
                .consensusTimestamp(consensusTimestamp)
                .historyProofVerificationKey(DomainUtils.toBytes(transactionBody.getHistoryProofVerificationKey()))
                .ledgerId(DomainUtils.toBytes(transactionBody.getLedgerId()))
                .network(importerProperties.getNetwork())
                .nodeContributions(nodeContributions)
                .build();
    }
}
