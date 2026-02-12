// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.hiero.mirror.importer.parser.domain.RecordItemBuilder;
import org.junit.jupiter.api.Test;

final class LedgerIdPublicationTransactionParserTest {

    @Test
    void parse() {
        // given
        var recordItemBuilder = new RecordItemBuilder();
        var recordItem = recordItemBuilder.ledgerIdPublication().build();
        var body = recordItem.getTransactionBody().getLedgerIdPublication();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var parser = new LedgerIdPublicationTransactionParser();

        // when
        var ledger = parser.parse(consensusTimestamp, body);

        // then
        var expectedNodeContributions = body.getNodeContributionsList().stream()
                .map(n -> LedgerNodeContribution.builder()
                        .historyProofKey(toBytes(n.getHistoryProofKey()))
                        .nodeId(n.getNodeId())
                        .weight(n.getWeight())
                        .build())
                .toList();
        assertThat(ledger)
                .returns(recordItem.getConsensusTimestamp(), Ledger::getConsensusTimestamp)
                .returns(toBytes(body.getHistoryProofVerificationKey()), Ledger::getHistoryProofVerificationKey)
                .returns(toBytes(body.getLedgerId()), Ledger::getLedgerId)
                .extracting(Ledger::getNodeContributions, LIST)
                .containsExactlyInAnyOrderElementsOf(expectedNodeContributions);
    }
}
