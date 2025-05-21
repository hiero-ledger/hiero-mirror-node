// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.data.util.Predicates.negate;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.parser.contractresult.SyntheticContractResultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

class CryptoApproveAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    protected SyntheticContractResultService syntheticContractResultService;

    private long consensusTimestamp;
    private CryptoAllowance expectedCryptoAllowance;
    private Nft expectedNft;
    private NftAllowance expectedNftAllowance;
    private TokenAllowance expectedTokenAllowance;
    private EntityId payerAccountId;

    @BeforeEach
    void beforeEach() {
        consensusTimestamp = DomainUtils.timestampInNanosMax(recordItemBuilder.timestamp());
        payerAccountId = EntityId.of(recordItemBuilder.accountId());
        var cryptoOwner = recordItemBuilder.accountId();
        expectedCryptoAllowance = CryptoAllowance.builder()
                .amountGranted(100L)
                .amount(100L)
                .owner(EntityId.of(cryptoOwner).getId())
                .payerAccountId(payerAccountId)
                .spender(EntityId.of(recordItemBuilder.accountId()).getId())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        when(entityIdService.lookup(cryptoOwner)).thenReturn(Optional.of(EntityId.of(cryptoOwner)));
        var nftOwner = recordItemBuilder.accountId();
        long nftTokenId = EntityId.of(recordItemBuilder.tokenId()).getId();
        expectedNft = Nft.builder()
                .accountId(EntityId.of(nftOwner))
                .serialNumber(1)
                .spender(EntityId.of(recordItemBuilder.accountId()).getId())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .tokenId(nftTokenId)
                .build();
        when(entityIdService.lookup(nftOwner)).thenReturn(Optional.of(expectedNft.getAccountId()));
        expectedNftAllowance = NftAllowance.builder()
                .approvedForAll(true)
                .owner(expectedNft.getAccountId().getId())
                .payerAccountId(payerAccountId)
                .spender(expectedNft.getSpender())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .tokenId(nftTokenId)
                .build();
        var tokenOwner = recordItemBuilder.accountId();
        expectedTokenAllowance = TokenAllowance.builder()
                .amountGranted(200L)
                .amount(200L)
                .owner(EntityId.of(tokenOwner).getId())
                .payerAccountId(payerAccountId)
                .spender(EntityId.of(recordItemBuilder.accountId()).getId())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .tokenId(EntityId.of(recordItemBuilder.tokenId()).getId())
                .build();
        when(entityIdService.lookup(tokenOwner)).thenReturn(Optional.of(EntityId.of(tokenOwner)));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoApproveAllowanceTransactionHandler(
                entityIdService, entityListener, syntheticContractLogService, syntheticContractResultService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoApproveAllowance(
                        CryptoApproveAllowanceTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(null);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessfulWithImplicitOwner() {
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBody(b -> {
                    b.getCryptoAllowancesBuilderList().forEach(builder -> builder.clearOwner());
                    b.getNftAllowancesBuilderList().forEach(builder -> builder.clearOwner());
                    b.getTokenAllowancesBuilderList().forEach(builder -> builder.clearOwner());
                })
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var effectiveOwner = recordItem.getPayerAccountId().getId();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(effectiveOwner);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionWithEmptyEntityId() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBody(b -> {
                    b.getCryptoAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getNftAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getTokenAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                })
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var transaction = domainBuilder.transaction().get();
        when(entityIdService.lookup(domainBuilder.entityId().toAccountID().toBuilder()
                        .setAlias(alias)
                        .build()))
                .thenReturn(Optional.of(EntityId.EMPTY));
        transactionHandler.updateTransaction(transaction, recordItem);

        // The implicit entity id is used
        var effectiveOwner = recordItem.getPayerAccountId().getId();
        assertAllowances(effectiveOwner);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void updateTransactionWithEmptyOwner(EntityId entityId) {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(b -> {
                    b.getCryptoAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().clear());
                    b.getNftAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getTokenAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                })
                .transactionBodyWrapper(w -> w.getTransactionIDBuilder()
                        .setAccountID(AccountID.newBuilder().setAccountNum(0)))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var transaction = domainBuilder.transaction().get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(Optional.ofNullable(entityId));
        var expectedEntityTransactions = super.getExpectedEntityTransactions(recordItem, transaction);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionWithAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var ownerAccountId = recordItemBuilder.accountId();
        var ownerEntityId = EntityId.of(ownerAccountId);
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBody(b -> {
                    b.getCryptoAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getNftAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getTokenAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                })
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        when(entityIdService.lookup(ownerAccountId.toBuilder().setAlias(alias).build()))
                .thenReturn(Optional.of(ownerEntityId));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(ownerEntityId.getId());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    private void assertAllowances(Long effectiveOwner) {
        if (effectiveOwner != null) {
            expectedCryptoAllowance.setOwner(effectiveOwner);
            expectedNft.setAccountId(EntityId.of(effectiveOwner));
            expectedNftAllowance.setOwner(effectiveOwner);
            expectedTokenAllowance.setOwner(effectiveOwner);
        }

        verify(entityListener, times(1)).onCryptoAllowance(assertArg(t -> assertEquals(expectedCryptoAllowance, t)));
        verify(entityListener, times(1)).onNft(assertArg(t -> assertEquals(expectedNft, t)));
        verify(entityListener, times(1)).onNftAllowance(assertArg(t -> assertEquals(expectedNftAllowance, t)));
        verify(entityListener, times(1)).onTokenAllowance(assertArg(t -> assertEquals(expectedTokenAllowance, t)));
    }

    private void customizeTransactionBody(CryptoApproveAllowanceTransactionBody.Builder builder) {
        builder.clear();

        var ownerAccountId = EntityId.of(expectedCryptoAllowance.getOwner()).toAccountID();
        var spenderAccountId = EntityId.of(expectedCryptoAllowance.getSpender()).toAccountID();
        // duplicate with different amount
        builder.addCryptoAllowances(com.hederahashgraph.api.proto.java.CryptoAllowance.newBuilder()
                .setAmount(expectedCryptoAllowance.getAmount() - 10)
                .setOwner(ownerAccountId)
                .setSpender(spenderAccountId));
        // the last one is honored
        builder.addCryptoAllowances(com.hederahashgraph.api.proto.java.CryptoAllowance.newBuilder()
                .setAmount(expectedCryptoAllowance.getAmount())
                .setOwner(ownerAccountId)
                .setSpender(spenderAccountId));

        var nftOwnerAccountId = expectedNft.getAccountId().toAccountID();
        var nftSpenderAccountId = EntityId.of(expectedNft.getSpender()).toAccountID();
        var nftTokenId = EntityId.of(expectedNft.getTokenId()).toTokenID();
        // duplicate nft allowance by serial
        builder.addNftAllowances(com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
                .setOwner(nftOwnerAccountId)
                .addSerialNumbers(expectedNft.getId().getSerialNumber())
                .setSpender(nftSpenderAccountId)
                .setTokenId(nftTokenId));
        // duplicate nft approved for all allowance, approved for all flag is flipped from the last one
        builder.addNftAllowances(com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(!expectedNftAllowance.isApprovedForAll()))
                .setOwner(nftOwnerAccountId)
                .addSerialNumbers(expectedNft.getId().getSerialNumber())
                .setSpender(nftSpenderAccountId)
                .setTokenId(nftTokenId));
        // the last one is honored
        builder.addNftAllowances(com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(expectedNftAllowance.isApprovedForAll()))
                .setOwner(nftOwnerAccountId)
                .addSerialNumbers(expectedNft.getId().getSerialNumber())
                .setSpender(nftSpenderAccountId)
                .setTokenId(nftTokenId));

        ownerAccountId = EntityId.of(expectedTokenAllowance.getOwner()).toAccountID();
        spenderAccountId = EntityId.of(expectedTokenAllowance.getSpender()).toAccountID();
        var tokenId = EntityId.of(expectedTokenAllowance.getTokenId()).toTokenID();
        // duplicate token allowance
        builder.addTokenAllowances(com.hederahashgraph.api.proto.java.TokenAllowance.newBuilder()
                .setAmount(expectedTokenAllowance.getAmount() - 10)
                .setOwner(ownerAccountId)
                .setSpender(spenderAccountId)
                .setTokenId(tokenId));
        // the last one is honored
        builder.addTokenAllowances(com.hederahashgraph.api.proto.java.TokenAllowance.newBuilder()
                .setAmount(expectedTokenAllowance.getAmount())
                .setOwner(ownerAccountId)
                .setSpender(spenderAccountId)
                .setTokenId(tokenId));
    }

    private Map<Long, EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem, Transaction transaction) {
        var entityIds = Stream.concat(
                Stream.of(
                        expectedNft.getAccountId(),
                        expectedNft.getDelegatingSpender(),
                        expectedNft.getDelegatingSpender()),
                Stream.of(
                                expectedCryptoAllowance.getOwner(),
                                expectedCryptoAllowance.getSpender(),
                                expectedTokenAllowance.getOwner(),
                                expectedTokenAllowance.getSpender(),
                                expectedTokenAllowance.getTokenId(),
                                expectedNftAllowance.getOwner(),
                                expectedNftAllowance.getSpender(),
                                expectedNftAllowance.getTokenId())
                        .filter(negate(Objects::isNull))
                        .map(EntityId::of));
        return getExpectedEntityTransactions(recordItem, transaction, entityIds.toArray(EntityId[]::new));
    }

    private void setTransactionPayer(TransactionBody.Builder builder) {
        builder.getTransactionIDBuilder().setAccountID(payerAccountId.toAccountID());
    }
}
