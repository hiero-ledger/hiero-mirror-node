// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.TokenCancelAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenAirdropStateEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class TokenCancelAirdropTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final EntityId receiver = domainBuilder.entityId();
    private final AccountID receiverAccountId = recordItemBuilder.accountId();
    private final EntityId sender = domainBuilder.entityId();
    private final AccountID senderAccountId = recordItemBuilder.accountId();

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenCancelAirdropTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenCancelAirdrop(
                        TokenCancelAirdropTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(receiverAccountId)).thenReturn(Optional.of(receiver));
        when(entityIdService.lookup(senderAccountId)).thenReturn(Optional.of(sender));
    }

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void cancelAirdrop(TokenTypeEnum tokenType) {
        // given
        var tokenAirdrop = ArgumentCaptor.forClass(TokenAirdrop.class);
        var token = recordItemBuilder.tokenId();
        var pendingAirdropId =
                PendingAirdropId.newBuilder().setReceiverId(receiverAccountId).setSenderId(senderAccountId);
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            pendingAirdropId.setFungibleTokenType(token);
        } else {
            pendingAirdropId.setNonFungibleToken(
                    NftID.newBuilder().setTokenID(token).setSerialNumber(1L));
        }
        var recordItem = recordItemBuilder
                .tokenCancelAirdrop()
                .transactionBody(b -> b.clearPendingAirdrops().addPendingAirdrops(pendingAirdropId.build()))
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();

        var tokenEntityId = EntityId.of(token);
        var expectedEntityTransactions =
                getExpectedEntityTransactions(recordItem, transaction, receiver, sender, tokenEntityId);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);

        verify(entityListener).onTokenAirdrop(tokenAirdrop.capture());
        assertThat(tokenAirdrop.getValue())
                .returns(receiver.getId(), TokenAirdrop::getReceiverAccountId)
                .returns(sender.getId(), TokenAirdrop::getSenderAccountId)
                .returns(TokenAirdropStateEnum.CANCELLED, TokenAirdrop::getState)
                .returns(Range.atLeast(timestamp), TokenAirdrop::getTimestampRange)
                .returns(tokenEntityId.getId(), TokenAirdrop::getTokenId);
        verifyNoMoreInteractions(entityListener);
    }
}
