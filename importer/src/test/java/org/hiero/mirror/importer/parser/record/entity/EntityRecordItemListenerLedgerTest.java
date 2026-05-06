// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.LedgerRepository;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class EntityRecordItemListenerLedgerTest extends AbstractEntityRecordItemListenerTest {

    private final LedgerRepository ledgerRepository;

    @Test
    void ledgerIdPublicationTransaction() {
        // given
        final var recordItem = recordItemBuilder.ledgerIdPublication().build();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        final var body = recordItem.getTransactionBody().getLedgerIdPublication();
        final var expectedNodeContributions = body.getNodeContributionsList().stream()
                .map(n -> LedgerNodeContribution.builder()
                        .historyProofKey(toBytes(n.getHistoryProofKey()))
                        .nodeId(n.getNodeId())
                        .weight(n.getWeight())
                        .build())
                .toList();
        assertThat(ledgerRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), Ledger::getConsensusTimestamp)
                .returns(toBytes(body.getHistoryProofVerificationKey()), Ledger::getHistoryProofVerificationKey)
                .returns(toBytes(body.getLedgerId()), Ledger::getLedgerId)
                .extracting(Ledger::getNodeContributions, LIST)
                .containsExactlyInAnyOrderElementsOf(expectedNodeContributions);
        final long validStartTimestamp = DomainUtils.timestampInNanosMax(
                recordItem.getTransactionBody().getTransactionID().getTransactionValidStart());
        assertThat(transactionRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), Transaction::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), Transaction::getPayerAccountId)
                .returns(validStartTimestamp, Transaction::getValidStartNs);
    }
}
