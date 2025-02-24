// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenRejectTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenRejectTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenRejectTransactionHandler(entityIdService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        var ownerId = AccountID.newBuilder().setAccountNum(DEFAULT_ENTITY_NUM).build();
        return TransactionBody.newBuilder()
                .setTokenReject(TokenRejectTransactionBody.newBuilder()
                        .setOwner(ownerId)
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @BeforeEach
    void beforeEach() {
        var ownerId = EntityId.of(DEFAULT_ENTITY_NUM);
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(Optional.of(ownerId));
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.tokenReject().build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionNoOwner() {
        var recordItem = recordItemBuilder
                .tokenReject()
                .transactionBody(b -> b.clearOwner())
                .build();
        testGetEntityIdHelper(
                recordItem.getTransactionBody(), recordItem.getTransactionRecord(), recordItem.getPayerAccountId());
    }
}
