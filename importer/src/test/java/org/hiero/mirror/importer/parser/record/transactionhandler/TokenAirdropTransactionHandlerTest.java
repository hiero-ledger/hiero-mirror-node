// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.token.TokenAirdropStateEnum.PENDING;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.TokenAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenAirdropTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenAirdropTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenAirdrop(TokenAirdropTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionSuccessfulFungiblePendingAirdrop() {
        // given
        var tokenAirdrop = ArgumentCaptor.forClass(TokenAirdrop.class);
        long amount = 5L;
        var receiver = recordItemBuilder.accountId();
        var sender = recordItemBuilder.accountId();
        var token = recordItemBuilder.tokenId();
        var fungibleAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(receiver)
                        .setSenderId(sender)
                        .setFungibleTokenType(token))
                .setPendingAirdropValue(
                        PendingAirdropValue.newBuilder().setAmount(amount).build());
        var recordItem = recordItemBuilder
                .tokenAirdrop()
                .record(r -> r.clearNewPendingAirdrops().addNewPendingAirdrops(fungibleAirdrop))
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();

        var receiverId = EntityId.of(receiver);
        var senderId = EntityId.of(sender);
        var tokenId = EntityId.of(token);
        var expectedEntityTransactions =
                getExpectedEntityTransactions(recordItem, transaction, receiverId, senderId, tokenId);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);

        verify(entityListener).onTokenAirdrop(tokenAirdrop.capture());
        assertThat(tokenAirdrop.getValue())
                .returns(amount, TokenAirdrop::getAmount)
                .returns(receiverId.getId(), TokenAirdrop::getReceiverAccountId)
                .returns(senderId.getId(), TokenAirdrop::getSenderAccountId)
                .returns(0L, TokenAirdrop::getSerialNumber)
                .returns(PENDING, TokenAirdrop::getState)
                .returns(Range.atLeast(timestamp), TokenAirdrop::getTimestampRange)
                .returns(tokenId.getId(), TokenAirdrop::getTokenId);
    }

    @Test
    void updateTransactionSuccessfulNftPendingAirdrop() {
        // given
        var tokenAirdrop = ArgumentCaptor.forClass(TokenAirdrop.class);
        var receiver = recordItemBuilder.accountId();
        var sender = recordItemBuilder.accountId();
        var token = recordItemBuilder.tokenId();
        var nftAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(receiver)
                        .setSenderId(sender)
                        .setNonFungibleToken(
                                NftID.newBuilder().setTokenID(token).setSerialNumber(1L)));
        var recordItem = recordItemBuilder
                .tokenAirdrop()
                .record(r -> r.clearNewPendingAirdrops().addNewPendingAirdrops(nftAirdrop))
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        var receiverId = EntityId.of(receiver);
        var senderId = EntityId.of(sender);
        var tokenId = EntityId.of(token);
        var expectedEntityTransactions =
                getExpectedEntityTransactions(recordItem, transaction, receiverId, senderId, tokenId);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);

        verify(entityListener).onTokenAirdrop(tokenAirdrop.capture());
        assertThat(tokenAirdrop.getValue())
                .returns(null, TokenAirdrop::getAmount)
                .returns(receiverId.getId(), TokenAirdrop::getReceiverAccountId)
                .returns(senderId.getId(), TokenAirdrop::getSenderAccountId)
                .returns(PENDING, TokenAirdrop::getState)
                .returns(Range.atLeast(timestamp), TokenAirdrop::getTimestampRange)
                .returns(tokenId.getId(), TokenAirdrop::getTokenId);
    }
}
