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
import java.nio.file.Files;
import java.nio.file.Path;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LedgerIdPublishTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @TempDir
    private Path dataPath;

    private ImporterProperties importerProperties;

    @Override
    protected TransactionHandler getTransactionHandler() {
        importerProperties = new ImporterProperties();
        importerProperties.setDataPath(dataPath);
        return new LedgerIdPublicationTransactionHandler(
                entityListener, importerProperties, new LedgerIdPublicationTransactionParser());
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
                .map(n -> LedgerNodeContribution.builder()
                        .historyProofKey(toBytes(n.getHistoryProofKey()))
                        .nodeId(n.getNodeId())
                        .weight(n.getWeight())
                        .build())
                .toList();
        verify(entityListener).onLedger(assertArg(ledger -> assertThat(ledger)
                .returns(recordItem.getConsensusTimestamp(), Ledger::getConsensusTimestamp)
                .returns(toBytes(body.getHistoryProofVerificationKey()), Ledger::getHistoryProofVerificationKey)
                .returns(toBytes(body.getLedgerId()), Ledger::getLedgerId)
                .extracting(Ledger::getNodeContributions, LIST)
                .containsExactlyInAnyOrderElementsOf(expectedNodeContributions)));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSavesNetworkLedgerToDisk() throws Exception {
        // given
        var recordItem = recordItemBuilder.ledgerIdPublication().build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var expectedBytes =
                recordItem.getTransactionBody().getLedgerIdPublication().toByteArray();
        var savedPath = dataPath.resolve("networkledger").resolve(importerProperties.getNetwork());
        assertThat(savedPath).exists();
        assertThat(Files.readAllBytes(savedPath)).isEqualTo(expectedBytes);
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
