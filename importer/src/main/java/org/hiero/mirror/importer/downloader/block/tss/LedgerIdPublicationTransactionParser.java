// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
public final class LedgerIdPublicationTransactionParser {

    public Ledger parse(final long consensusTimestamp, final LedgerIdPublicationTransactionBody transactionBody) {
        final var protoNodeContributions = transactionBody.getNodeContributionsList();
        final List<LedgerNodeContribution> nodeContributions = new ArrayList<>(protoNodeContributions.size());
        for (final var nodeContribution : protoNodeContributions) {
            nodeContributions.add(LedgerNodeContribution.builder()
                    .historyProofKey(DomainUtils.toBytes(nodeContribution.getHistoryProofKey()))
                    .nodeId(nodeContribution.getNodeId())
                    .weight(nodeContribution.getWeight())
                    .build());
        }

        return Ledger.builder()
                .consensusTimestamp(consensusTimestamp)
                .historyProofVerificationKey(DomainUtils.toBytes(transactionBody.getHistoryProofVerificationKey()))
                .ledgerId(DomainUtils.toBytes(transactionBody.getLedgerId()))
                .nodeContributions(nodeContributions)
                .build();
    }
}
