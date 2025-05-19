// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.junit.jupiter.api.Test;

class CryptoDeleteAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoDeleteAllowanceTransactionHandler(entityListener, syntheticContractLogService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoDeleteAllowance(
                        CryptoDeleteAllowanceTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder.cryptoDeleteAllowance().build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(null))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(timestamp);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessfulWithImplicitOwner() {
        var recordItem = recordItemBuilder
                .cryptoDeleteAllowance()
                .transactionBody(b -> b.getNftAllowancesBuilderList().forEach(NftRemoveAllowance.Builder::clearOwner))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(null))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(timestamp);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    private void assertAllowances(long timestamp) {
        verify(entityListener, times(4)).onNft(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(null, Nft::getAccountId)
                .returns(null, Nft::getCreatedTimestamp)
                .returns(null, Nft::getDelegatingSpender)
                .returns(null, Nft::getDeleted)
                .returns(null, Nft::getMetadata)
                .satisfies(n -> assertThat(n.getId().getSerialNumber()).isPositive())
                .returns(null, Nft::getSpender)
                .returns(Range.atLeast(timestamp), Nft::getTimestampRange)));
    }

    private Map<Long, EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem, Transaction transaction) {
        var body = recordItem.getTransactionBody().getCryptoDeleteAllowance();
        var payerAccountId = recordItem.getPayerAccountId();
        var entityIds = body.getNftAllowancesList().stream().flatMap(allowance -> {
            var owner = allowance.getOwner().equals(AccountID.getDefaultInstance())
                    ? payerAccountId
                    : EntityId.of(allowance.getOwner());
            return Stream.of(owner, EntityId.of(allowance.getTokenId()));
        });
        return getExpectedEntityTransactions(recordItem, transaction, entityIds.toArray(EntityId[]::new));
    }
}
