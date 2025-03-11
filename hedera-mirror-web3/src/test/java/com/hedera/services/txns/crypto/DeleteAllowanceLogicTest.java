// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto;

import static com.hedera.services.store.models.Id.fromGrpcToken;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.EntityNum.fromTokenId;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.common.Web3UnitTest;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteAllowanceLogicTest extends Web3UnitTest {
    @Mock
    private Store store;

    private TransactionBody cryptoDeleteAllowanceTxn;
    private CryptoDeleteAllowanceTransactionBody op;

    DeleteAllowanceLogic subject = new DeleteAllowanceLogic();

    private static final TokenID token1 = asToken("0.0.100");
    private static final TokenID token2 = asToken("0.0.200");
    private static final AccountID ownerId = asAccount("0.0.5000");
    private static final AccountID payerId = asAccount("0.0.5001");
    private static final Instant consensusTime = Instant.now();
    private Token token1Model = new Token(Id.fromGrpcToken(token1));
    private Token token2Model = new Token(Id.fromGrpcToken(token2));

    private final NftRemoveAllowance nftAllowance1 = NftRemoveAllowance.newBuilder()
            .setOwner(ownerId)
            .setTokenId(token2)
            .addAllSerialNumbers(List.of(12L, 10L))
            .build();
    private List<NftRemoveAllowance> nftAllowances = new ArrayList<>();
    private Account ownerAccount = new Account(0L, Id.fromGrpcAccount(ownerId), 0L);
    private Account payerAccount = new Account(0L, Id.fromGrpcAccount(payerId), 0L);

    private final SortedSet<FcTokenAllowanceId> existingNftAllowances = new TreeSet<>();

    private static final AccountID spender1 = asAccount("0.0.123");
    private static final AccountID spender2 = asAccount("0.0.1234");

    private static UniqueToken uniqueToken1 = new UniqueToken(fromGrpcToken(token2), 12L, null, null, null, null);
    private static UniqueToken uniqueToken2 = new UniqueToken(fromGrpcToken(token2), 10L, null, null, null, null);

    @Test
    void happyPathDeletesAllowances() {
        uniqueToken1 = uniqueToken1.setOwner(Id.DEFAULT);
        uniqueToken2 = uniqueToken2.setOwner(Id.DEFAULT);
        givenValidTxnCtx();
        ownerAccount = addExistingAllowances(ownerAccount);
        given(store.getAccount(asTypedEvmAddress(payerId), OnMissing.THROW)).willReturn(payerAccount);
        given(store.loadAccountOrFailWith(asTypedEvmAddress(ownerId), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);

        token2Model = token2Model.setTreasury(ownerAccount);
        final var nftId2 = new NftId(token2.getShardNum(), token2.getRealmNum(), token2.getTokenNum(), 12L);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken2);
        given(store.loadPossiblyPausedToken(token2Model.getId().asEvmAddress())).willReturn(token2Model);

        assertEquals(1, ownerAccount.getApproveForAllNfts().size());

        subject.deleteAllowance(store, new ArrayList<>(), op.getNftAllowancesList(), payerId);

        assertEquals(1, ownerAccount.getApproveForAllNfts().size());
        verify(store, times(2)).updateUniqueToken(any());
    }

    @Test
    void canDeleteAllowancesOnTreasury() {
        uniqueToken1 = uniqueToken1.setOwner(Id.DEFAULT);
        uniqueToken2 = uniqueToken2.setOwner(Id.DEFAULT);
        givenValidTxnCtx();
        ownerAccount = addExistingAllowances(ownerAccount);
        given(store.getAccount(asTypedEvmAddress(payerId), OnMissing.THROW)).willReturn(payerAccount);
        given(store.loadAccountOrFailWith(asTypedEvmAddress(ownerId), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);
        token2Model = token2Model.setTreasury(ownerAccount);
        final var nftId2 = new NftId(token2.getShardNum(), token2.getRealmNum(), token2.getTokenNum(), 12L);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken2);
        given(store.loadPossiblyPausedToken(token2Model.getId().asEvmAddress())).willReturn(token2Model);

        assertEquals(1, ownerAccount.getApproveForAllNfts().size());

        subject.deleteAllowance(store, new ArrayList<>(), op.getNftAllowancesList(), payerId);

        assertEquals(1, ownerAccount.getApproveForAllNfts().size());
        verify(store, times(2)).updateUniqueToken(any());
    }

    @Test
    void failsDeleteAllowancesOnInvalidTreasury() {
        uniqueToken1 = uniqueToken1.setOwner(Id.DEFAULT);
        uniqueToken2 = uniqueToken2.setOwner(Id.DEFAULT);
        givenValidTxnCtx();

        given(store.getAccount(asTypedEvmAddress(payerId), OnMissing.THROW)).willReturn(payerAccount);
        given(store.loadAccountOrFailWith(asTypedEvmAddress(ownerId), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);
        token2Model = token2Model.setTreasury(payerAccount);
        final var nftId2 = new NftId(token2.getShardNum(), token2.getRealmNum(), token2.getTokenNum(), 12L);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken2);
        given(store.loadPossiblyPausedToken(token2Model.getId().asEvmAddress())).willReturn(token2Model);

        Executable deleteAllowance =
                () -> subject.deleteAllowance(store, new ArrayList<>(), op.getNftAllowancesList(), payerId);

        assertThrows(InvalidTransactionException.class, deleteAllowance);
    }

    @Test
    void doesntThrowIfAllowancesDoesNotExist() {
        final NftRemoveAllowance nftRemoveAllowance =
                NftRemoveAllowance.newBuilder().setOwner(ownerId).build();

        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoDeleteAllowance(
                        CryptoDeleteAllowanceTransactionBody.newBuilder().addNftAllowances(nftRemoveAllowance))
                .build();

        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        given(store.getAccount(asTypedEvmAddress(payerId), OnMissing.THROW)).willReturn(payerAccount);
        given(store.loadAccountOrFailWith(asTypedEvmAddress(ownerId), INVALID_ALLOWANCE_OWNER_ID))
                .willReturn(ownerAccount);

        subject.deleteAllowance(store, new ArrayList<>(), op.getNftAllowancesList(), payerId);

        verify(store, never()).updateUniqueToken(any());
    }

    @Test
    void clearsPayerIfOwnerNotSpecified() {
        uniqueToken1 = uniqueToken1.setOwner(payerAccount.getId());
        uniqueToken2 = uniqueToken2.setOwner(payerAccount.getId());
        givenValidTxnCtxWithNoOwner();
        payerAccount = addExistingAllowances(payerAccount);

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        final var nftId2 = new NftId(token2.getShardNum(), token2.getRealmNum(), token2.getTokenNum(), 12L);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken2);

        assertEquals(1, payerAccount.getApproveForAllNfts().size());

        subject.deleteAllowance(store, new ArrayList<>(), op.getNftAllowancesList(), payerId);

        assertEquals(1, payerAccount.getApproveForAllNfts().size());
        verify(store, times(2)).updateUniqueToken(any());
    }

    @Test
    void emptyAllowancesInStateTransitionWorks() {
        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder())
                .build();

        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        given(store.getAccount(asTypedEvmAddress(payerId), OnMissing.THROW)).willReturn(payerAccount);

        subject.deleteAllowance(store, new ArrayList<>(), op.getNftAllowancesList(), payerId);

        assertEquals(0, ownerAccount.getCryptoAllowances().size());
        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(0, ownerAccount.getApproveForAllNfts().size());
        verify(store, never()).updateAccount(ownerAccount);
    }

    private void givenValidTxnCtx() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);
        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        nftAllowances.add(nftAllowance1);

        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoDeleteAllowance(
                        CryptoDeleteAllowanceTransactionBody.newBuilder().addAllNftAllowances(nftAllowances))
                .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        ownerAccount.setApproveForAllNfts(new TreeSet<>());
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payerId)
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
                .build();
    }

    private Account addExistingAllowances(Account ownerAccount) {
        List<Long> serials = new ArrayList<>();
        serials.add(10L);
        serials.add(12L);

        existingNftAllowances.add(FcTokenAllowanceId.from(fromTokenId(token2), fromAccountId(spender1)));

        ownerAccount = ownerAccount.setApproveForAllNfts(existingNftAllowances);

        uniqueToken1 = uniqueToken1.setSpender(Id.fromGrpcAccount(spender1));
        uniqueToken2 = uniqueToken2.setSpender(Id.fromGrpcAccount(spender2));
        final var nftId2 = new NftId(token2.getShardNum(), token2.getRealmNum(), token2.getTokenNum(), 10L);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken2);
        return ownerAccount;
    }

    private void givenValidTxnCtxWithNoOwner() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);

        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final NftRemoveAllowance nftAllowance = NftRemoveAllowance.newBuilder()
                .setTokenId(token2)
                .addAllSerialNumbers(List.of(12L, 10L))
                .build();

        nftAllowances.add(nftAllowance);

        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoDeleteAllowance(
                        CryptoDeleteAllowanceTransactionBody.newBuilder().addAllNftAllowances(nftAllowances))
                .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        ownerAccount = ownerAccount.setApproveForAllNfts(new TreeSet<>());

        given(store.getAccount(asTypedEvmAddress(payerId), OnMissing.THROW)).willReturn(payerAccount);
    }
}
