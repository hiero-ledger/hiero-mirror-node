// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto;

import static com.hedera.services.store.models.Id.fromGrpcAccount;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.EntityNum.fromTokenId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.BoolValue;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApproveAllowanceLogicTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock
    private Store store;

    private TransactionBody cryptoApproveAllowanceTxn;
    private CryptoApproveAllowanceTransactionBody op;

    ApproveAllowanceLogic subject = new ApproveAllowanceLogic();

    private static final long SERIAL_1 = 1L;
    private static final long SERIAL_2 = 10L;
    private static final AccountID SPENDER_1 = domainBuilder.entityId().toAccountID();
    private static final TokenID TOKEN_1 = domainBuilder.entityId().toTokenID();
    private static final TokenID TOKEN_2 = domainBuilder.entityId().toTokenID();
    private static final AccountID PAYER_ID = domainBuilder.entityId().toAccountID();
    private static final AccountID OWNER_ID = domainBuilder.entityId().toAccountID();
    private static final Id TOKEN_ID_1 = Id.fromGrpcToken(TOKEN_1);
    private static final Id TOKEN_ID_2 = Id.fromGrpcToken(TOKEN_2);
    private static final Id SPENDER_ID_1 = fromGrpcAccount(SPENDER_1);
    private static final Instant CONSENSUS_TIME = Instant.now();
    private Token token1Model = new Token(Id.fromGrpcToken(TOKEN_1));
    private Token token2Model = new Token(Id.fromGrpcToken(TOKEN_2));
    private final CryptoAllowance cryptoAllowanceOne = CryptoAllowance.newBuilder()
            .setSpender(SPENDER_1)
            .setOwner(OWNER_ID)
            .setAmount(10L)
            .build();
    private final TokenAllowance tokenAllowanceOne = TokenAllowance.newBuilder()
            .setSpender(SPENDER_1)
            .setAmount(10L)
            .setTokenId(TOKEN_1)
            .setOwner(OWNER_ID)
            .build();
    private final NftAllowance nftAllowanceOne = NftAllowance.newBuilder()
            .setSpender(SPENDER_1)
            .setOwner(OWNER_ID)
            .setTokenId(TOKEN_2)
            .setApprovedForAll(BoolValue.of(true))
            .addAllSerialNumbers(List.of(SERIAL_1, SERIAL_2))
            .build();
    private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
    private List<TokenAllowance> tokenAllowances = new ArrayList<>();
    private List<NftAllowance> nftAllowances = new ArrayList<>();
    private Account payerAccount = new Account(0L, fromGrpcAccount(PAYER_ID), 0L);
    private Account ownerAccount = new Account(0L, fromGrpcAccount(OWNER_ID), 0L);
    private UniqueToken nft1 = new UniqueToken(TOKEN_ID_1, SERIAL_1, null, fromGrpcAccount(OWNER_ID), null, null);
    private UniqueToken nft2 = new UniqueToken(TOKEN_ID_2, SERIAL_2, null, fromGrpcAccount(OWNER_ID), null, null);

    @Test
    void happyPathAddsAllowances() {
        givenValidTxnCtx();

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        given(store.loadAccountOrFailWith(ownerAccount.getAccountAddress(), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_2), OnMissing.THROW))
                .willReturn(nft2);

        final var accountsChanged = new TreeMap<Long, Account>();
        subject.approveAllowance(
                store,
                accountsChanged,
                new TreeMap<>(),
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(PAYER_ID).asGrpcAccount());

        final var updatedAccount = accountsChanged.get(ownerAccount.getId().num());
        assertEquals(1, updatedAccount.getCryptoAllowances().size());
        assertEquals(1, updatedAccount.getFungibleTokenAllowances().size());
        assertEquals(1, updatedAccount.getApproveForAllNfts().size());

        verify(store).updateAccount(updatedAccount);
    }

    @Test
    void considersPayerAsOwnerIfNotMentioned() {
        givenValidTxnCtxWithOwnerAsPayer();
        nft1 = new UniqueToken(TOKEN_ID_1, SERIAL_1, null, fromGrpcAccount(PAYER_ID), null, null);
        nft2 = new UniqueToken(TOKEN_ID_2, SERIAL_2, null, fromGrpcAccount(PAYER_ID), null, null);

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_2), OnMissing.THROW))
                .willReturn(nft2);

        assertEquals(0, payerAccount.getCryptoAllowances().size());
        assertEquals(0, payerAccount.getFungibleTokenAllowances().size());
        assertEquals(0, payerAccount.getApproveForAllNfts().size());

        final var accountsChanged = new TreeMap<Long, Account>();
        subject.approveAllowance(
                store,
                accountsChanged,
                new TreeMap<>(),
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(PAYER_ID).asGrpcAccount());

        final var updatedAccount = accountsChanged.get(payerAccount.getId().num());

        verify(store).updateAccount(updatedAccount);

        assertEquals(1, updatedAccount.getCryptoAllowances().size());
        assertEquals(1, updatedAccount.getFungibleTokenAllowances().size());
        assertEquals(1, updatedAccount.getApproveForAllNfts().size());
    }

    @Test
    void emptyAllowancesInStateTransitionWorks() {
        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder())
                .build();

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        subject.approveAllowance(
                store,
                new TreeMap<>(),
                new TreeMap<>(),
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(PAYER_ID).asGrpcAccount());

        assertEquals(0, ownerAccount.getCryptoAllowances().size());
        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(0, ownerAccount.getApproveForAllNfts().size());
    }

    @Test
    void doesntAddAllowancesWhenAmountIsZero() {
        givenTxnCtxWithZeroAmount();

        given(store.loadAccountOrFailWith(ownerAccount.getAccountAddress(), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);
        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_2), OnMissing.THROW))
                .willReturn(nft2);

        subject.approveAllowance(
                store,
                new TreeMap<>(),
                new TreeMap<>(),
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(PAYER_ID).asGrpcAccount());

        assertEquals(0, ownerAccount.getCryptoAllowances().size());
        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(0, ownerAccount.getApproveForAllNfts().size());
    }

    @Test
    void skipsTxnWhenKeyExistsAndAmountGreaterThanZero() {
        var ownerAcc = new Account(0L, fromGrpcAccount(OWNER_ID), 0L);
        ownerAcc = setUpOwnerWithExistingKeys(ownerAcc);

        assertEquals(1, ownerAcc.getCryptoAllowances().size());
        assertEquals(1, ownerAcc.getFungibleTokenAllowances().size());
        assertEquals(1, ownerAcc.getApproveForAllNfts().size());

        givenValidTxnCtx();

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        given(store.loadAccountOrFailWith(ownerAcc.getAccountAddress(), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAcc);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_2), OnMissing.THROW))
                .willReturn(nft2);

        subject.approveAllowance(
                store,
                new TreeMap<>(),
                new TreeMap<>(),
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(PAYER_ID).asGrpcAccount());

        assertEquals(1, ownerAcc.getCryptoAllowances().size());
        assertEquals(1, ownerAcc.getFungibleTokenAllowances().size());
        assertEquals(1, ownerAcc.getApproveForAllNfts().size());
    }

    @Test
    void checkIfApproveForAllIsSet() {
        final NftAllowance nftAllowance = NftAllowance.newBuilder()
                .setSpender(SPENDER_1)
                .setOwner(OWNER_ID)
                .setTokenId(TOKEN_2)
                .addAllSerialNumbers(List.of(SERIAL_1))
                .build();
        final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
                .setSpender(SPENDER_1)
                .setOwner(OWNER_ID)
                .setTokenId(TOKEN_2)
                .setApprovedForAll(BoolValue.of(false))
                .addAllSerialNumbers(List.of(SERIAL_1))
                .build();
        nftAllowances.add(nftAllowance);
        nftAllowances.add(nftAllowance1);

        var ownerAcccount = new Account(0L, fromGrpcAccount(OWNER_ID), 0L);

        givenValidTxnCtx();

        given(store.loadAccountOrFailWith(SPENDER_ID_1.asEvmAddress(), INVALID_ALLOWANCE_SPENDER_ID))
                .willReturn(payerAccount);
        ownerAcccount.setCryptoAllowance(new TreeMap<>());
        ownerAcccount.setFungibleTokenAllowances(new TreeMap<>());
        ownerAcccount.setApproveForAllNfts(new TreeSet<>());
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_2), OnMissing.THROW))
                .willReturn(nft2);

        final var accountsChanged = new TreeMap<Long, Account>();
        subject.applyNftAllowances(store, accountsChanged, new TreeMap<>(), nftAllowances, ownerAcccount);

        final var approveForAllNfts = new TreeSet<FcTokenAllowanceId>();
        approveForAllNfts.add(
                FcTokenAllowanceId.from(EntityNum.fromTokenId(TOKEN_2), EntityNum.fromAccountId(SPENDER_1)));

        final var updatedAccount = ownerAccount.setApproveForAllNfts(approveForAllNfts);

        assertEquals(updatedAccount, accountsChanged.get(ownerAcccount.getId().num()));
    }

    @Test
    void overridesExistingAllowances() {
        givenValidTxnCtxForOverwritingAllowances();
        addExistingAllowances();

        assertEquals(1, ownerAccount.getCryptoAllowances().size());
        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(2, ownerAccount.getApproveForAllNfts().size());
        assertEquals(
                20,
                ownerAccount.getCryptoAllowances().get(fromAccountId(SPENDER_1)).intValue());
        assertEquals(
                20,
                ownerAccount
                        .getFungibleTokenAllowances()
                        .get(FcTokenAllowanceId.from(fromTokenId(TOKEN_1), fromAccountId(SPENDER_1)))
                        .intValue());
        assertTrue(ownerAccount
                .getApproveForAllNfts()
                .contains(FcTokenAllowanceId.from(fromTokenId(TOKEN_1), fromAccountId(SPENDER_1))));
        assertTrue(ownerAccount
                .getApproveForAllNfts()
                .contains(FcTokenAllowanceId.from(fromTokenId(TOKEN_1), fromAccountId(SPENDER_1))));

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        given(store.loadAccountOrFailWith(ownerAccount.getAccountAddress(), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(TOKEN_ID_2.shard(), TOKEN_ID_2.realm(), TOKEN_ID_2.num(), SERIAL_2), OnMissing.THROW))
                .willReturn(nft2);

        final var accountsChanged = new TreeMap<Long, Account>();
        subject.approveAllowance(
                store,
                accountsChanged,
                new TreeMap<>(),
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(PAYER_ID).asGrpcAccount());

        final var updatedAccount = accountsChanged.get(ownerAccount.getId().num());
        assertEquals(1, updatedAccount.getCryptoAllowances().size());
        assertEquals(1, updatedAccount.getFungibleTokenAllowances().size());
        assertEquals(1, updatedAccount.getApproveForAllNfts().size());
        assertEquals(
                10,
                updatedAccount
                        .getCryptoAllowances()
                        .get(fromAccountId(SPENDER_1))
                        .intValue());
        assertEquals(
                10,
                updatedAccount
                        .getFungibleTokenAllowances()
                        .get(FcTokenAllowanceId.from(fromTokenId(TOKEN_1), fromAccountId(SPENDER_1)))
                        .intValue());
        assertTrue(updatedAccount
                .getApproveForAllNfts()
                .contains(FcTokenAllowanceId.from(fromTokenId(TOKEN_1), fromAccountId(SPENDER_1))));
        assertFalse(updatedAccount
                .getApproveForAllNfts()
                .contains(FcTokenAllowanceId.from(fromTokenId(TOKEN_2), fromAccountId(SPENDER_1))));
    }

    private void addExistingAllowances() {
        final SortedMap<EntityNum, Long> existingCryptoAllowances = new TreeMap<>();
        final SortedMap<FcTokenAllowanceId, Long> existingTokenAllowances = new TreeMap<>();
        final SortedSet<FcTokenAllowanceId> existingNftAllowances = new TreeSet<>();

        existingCryptoAllowances.put(fromAccountId(SPENDER_1), 20L);
        existingTokenAllowances.put(FcTokenAllowanceId.from(fromTokenId(TOKEN_1), fromAccountId(SPENDER_1)), 20L);
        existingNftAllowances.add(FcTokenAllowanceId.from(fromTokenId(TOKEN_2), fromAccountId(SPENDER_1)));
        existingNftAllowances.add(FcTokenAllowanceId.from(fromTokenId(TOKEN_1), fromAccountId(SPENDER_1)));
        ownerAccount = ownerAccount
                .setCryptoAllowance(existingCryptoAllowances)
                .setFungibleTokenAllowances(existingTokenAllowances)
                .setApproveForAllNfts(existingNftAllowances);
    }

    private void givenValidTxnCtxForOverwritingAllowances() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);
        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final NftAllowance nftAllowance2 = NftAllowance.newBuilder()
                .setSpender(SPENDER_1)
                .setOwner(OWNER_ID)
                .setTokenId(TOKEN_2)
                .setApprovedForAll(BoolValue.of(false))
                .addAllSerialNumbers(List.of(SERIAL_1, SERIAL_2))
                .build();

        cryptoAllowances.add(cryptoAllowanceOne);
        tokenAllowances.add(tokenAllowanceOne);
        nftAllowances.add(nftAllowanceOne);
        nftAllowances.add(nftAllowance2);

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances))
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        ownerAccount = ownerAccount
                .setApproveForAllNfts(new TreeSet<>())
                .setCryptoAllowance(new TreeMap<>())
                .setFungibleTokenAllowances(new TreeMap<>());
    }

    private void givenValidTxnCtx() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);
        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        cryptoAllowances.add(cryptoAllowanceOne);
        tokenAllowances.add(tokenAllowanceOne);
        nftAllowances.add(nftAllowanceOne);

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances))
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        ownerAccount = ownerAccount
                .setApproveForAllNfts(new TreeSet<>())
                .setCryptoAllowance(new TreeMap<>())
                .setFungibleTokenAllowances(new TreeMap<>());
    }

    private void givenTxnCtxWithZeroAmount() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);
        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder()
                .setOwner(OWNER_ID)
                .setSpender(SPENDER_1)
                .setAmount(0L)
                .build();
        final TokenAllowance tokenAllowance = TokenAllowance.newBuilder()
                .setSpender(SPENDER_1)
                .setAmount(0L)
                .setTokenId(TOKEN_1)
                .setOwner(OWNER_ID)
                .build();
        final NftAllowance nftAllowance = NftAllowance.newBuilder()
                .setSpender(SPENDER_1)
                .setTokenId(TOKEN_2)
                .setApprovedForAll(BoolValue.of(false))
                .setOwner(OWNER_ID)
                .addAllSerialNumbers(List.of(1L, 10L))
                .build();

        cryptoAllowances.add(cryptoAllowanceOne);
        tokenAllowances.add(tokenAllowanceOne);
        nftAllowances.add(nftAllowanceOne);
        cryptoAllowances.add(cryptoAllowance);
        tokenAllowances.add(tokenAllowance);
        nftAllowances.add(nftAllowance);

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances))
                .build();

        ownerAccount = ownerAccount
                .setApproveForAllNfts(new TreeSet<>())
                .setCryptoAllowance(new TreeMap<>())
                .setFungibleTokenAllowances(new TreeMap<>());
    }

    private void givenValidTxnCtxWithOwnerAsPayer() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);
        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder()
                .setSpender(SPENDER_1)
                .setAmount(10L)
                .build();
        final TokenAllowance tokenAllowance = TokenAllowance.newBuilder()
                .setSpender(SPENDER_1)
                .setAmount(10L)
                .setTokenId(TOKEN_1)
                .build();
        final NftAllowance nftAllowance = NftAllowance.newBuilder()
                .setSpender(SPENDER_1)
                .setTokenId(TOKEN_2)
                .setApprovedForAll(BoolValue.of(true))
                .addAllSerialNumbers(List.of(1L, 10L))
                .build();

        cryptoAllowances.add(cryptoAllowance);
        tokenAllowances.add(tokenAllowance);
        nftAllowances.add(nftAllowance);

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances))
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        payerAccount = payerAccount
                .setApproveForAllNfts(new TreeSet<>())
                .setCryptoAllowance(new TreeMap<>())
                .setFungibleTokenAllowances(new TreeMap<>());
    }

    private Account setUpOwnerWithExistingKeys(Account ownerAccount) {
        SortedMap<EntityNum, Long> cryptoAllowancesMap = new TreeMap<>();
        SortedMap<FcTokenAllowanceId, Long> tokenAllowancesMap = new TreeMap<>();
        SortedSet<FcTokenAllowanceId> nftAllowancesMap = new TreeSet<>();
        final var id = FcTokenAllowanceId.from(fromTokenId(TOKEN_1), fromAccountId(SPENDER_1));
        final var nftId = FcTokenAllowanceId.from(fromTokenId(TOKEN_2), fromAccountId(SPENDER_1));
        cryptoAllowancesMap.put(fromAccountId(SPENDER_1), 10000L);
        tokenAllowancesMap.put(id, 100000L);
        nftAllowancesMap.add(nftId);
        return ownerAccount
                .setApproveForAllNfts(nftAllowancesMap)
                .setCryptoAllowance(cryptoAllowancesMap)
                .setFungibleTokenAllowances(tokenAllowancesMap);
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(PAYER_ID)
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(CONSENSUS_TIME.getEpochSecond()))
                .build();
    }
}
