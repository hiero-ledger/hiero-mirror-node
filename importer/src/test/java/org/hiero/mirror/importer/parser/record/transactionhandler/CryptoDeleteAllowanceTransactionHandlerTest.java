// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;

class CryptoDeleteAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoDeleteAllowanceTransactionHandler(
                entityIdService, entityListener, syntheticContractLogService);
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
        mockOwnerLookups(recordItem);
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
        mockOwnerLookups(recordItem);
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
    void updateTransactionSuccessfulWithAliasOwner() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var ownerAccountId = recordItemBuilder.accountId();
        var ownerEntityId = EntityId.of(ownerAccountId);
        var recordItem = recordItemBuilder
                .cryptoDeleteAllowance()
                .transactionBody(b -> b.getNftAllowancesBuilderList()
                        .forEach(builder -> builder.getOwnerBuilder().setAlias(alias)))
                .build();
        when(entityIdService.lookup(
                        ownerAccountId.toBuilder().setAlias(alias).build(),
                        recordItem.getPayerAccountId().toAccountID()))
                .thenReturn(Optional.of(ownerEntityId));
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(null))
                .get();

        transactionHandler.updateTransaction(transaction, recordItem);

        assertAllowances(timestamp);
        // Both nft allowances share the same alias-addressed owner, distinct from the payer
        var body = recordItem.getTransactionBody().getCryptoDeleteAllowance();
        var entityIds = body.getNftAllowancesList().stream()
                .flatMap(allowance -> Stream.of(ownerEntityId, EntityId.of(allowance.getTokenId())));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, entityIds.toArray(EntityId[]::new)));
    }

    private void mockOwnerLookups(RecordItem recordItem) {
        var payerAccountId = recordItem.getPayerAccountId().toAccountID();
        recordItem
                .getTransactionBody()
                .getCryptoDeleteAllowance()
                .getNftAllowancesList()
                .forEach(allowance -> {
                    var owner = allowance.getOwner();
                    var resolvedOwner = owner.equals(AccountID.getDefaultInstance())
                            ? recordItem.getPayerAccountId()
                            : EntityId.of(owner);
                    when(entityIdService.lookup(owner, payerAccountId)).thenReturn(Optional.of(resolvedOwner));
                });
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
