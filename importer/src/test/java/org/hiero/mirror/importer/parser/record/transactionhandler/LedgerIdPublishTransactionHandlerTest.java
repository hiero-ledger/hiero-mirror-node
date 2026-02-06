// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.ledger.Ledger;
import org.hiero.mirror.common.domain.ledger.NodeContribution;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.junit.jupiter.api.Test;

final class LedgerIdPublishTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private String network;

    @Override
    protected TransactionHandler getTransactionHandler() {
        var importerProperties = new ImporterProperties();
        network = importerProperties.getNetwork();
        var parser = new LedgerIdPublicationTransactionParser(importerProperties);
        return new LedgerIdPublicationTransactionHandler(entityListener, parser);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setLedgerIdPublication(LedgerIdPublicationTransactionBody.getDefaultInstance());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        // given
        var recordItem = recordItemBuilder.ledgerIdPublication().build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var body = recordItem.getTransactionBody().getLedgerIdPublication();
        var expectedNodeContributions = body.getNodeContributionsList().stream()
                .map(n -> NodeContribution.builder()
                        .historyProofKey(toBytes(n.getHistoryProofKey()))
                        .nodeId(n.getNodeId())
                        .weight(n.getWeight())
                        .build())
                .toList();
        verify(entityListener).onLedger(assertArg(ledger -> assertThat(ledger)
                .returns(recordItem.getConsensusTimestamp(), Ledger::getConsensusTimestamp)
                .returns(toBytes(body.getHistoryProofVerificationKey()), Ledger::getHistoryProofVerificationKey)
                .returns(toBytes(body.getLedgerId()), Ledger::getLedgerId)
                .returns(network, Ledger::getNetwork)
                .extracting(Ledger::getNodeContributions, LIST)
                .containsExactlyInAnyOrderElementsOf(expectedNodeContributions)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionUnsuccessful() {
        // given
        var recordItem = recordItemBuilder
                .ledgerIdPublication()
                .status(ResponseCodeEnum.INVALID_SIGNATURE)
                .build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verify(entityListener, never()).onLedger(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
