// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import com.google.common.collect.Range;
import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.junit.jupiter.api.Test;

final class LedgerIdPublicationTransactionParserTest {

    @Test
    void parse() {
        // given
        final var recordItemBuilder = new RecordItemBuilder();
        final var recordItem = recordItemBuilder.ledgerIdPublication().build();
        final var body = recordItem.getTransactionBody().getLedgerIdPublication();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var parser = new LedgerIdPublicationTransactionParser();

        // when
        final var ledger = parser.parse(consensusTimestamp, body);

        // then
        final var expectedNodeContributions = body.getNodeContributionsList().stream()
                .map(n -> LedgerNodeContribution.builder()
                        .historyProofKey(toBytes(n.getHistoryProofKey()))
                        .nodeId(n.getNodeId())
                        .weight(n.getWeight())
                        .build())
                .toList();
        assertThat(ledger)
                .returns(toBytes(body.getLedgerId()), Ledger::getLedgerId)
                .returns(Range.atLeast(consensusTimestamp), Ledger::getTimestampRange)
                .extracting(Ledger::getNodeContributions, LIST)
                .containsExactlyInAnyOrderElementsOf(expectedNodeContributions);
    }
}
