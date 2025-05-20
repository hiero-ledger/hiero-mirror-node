// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txns.crypto.validators;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
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
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.tuple.Pair;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteAllowanceChecksTest {

    @Mock
    private Store store;

    @Mock
    private Account payer;

    @Mock
    private Account ownerAccount;

    @Mock
    private Token merkleToken;

    @Mock
    private UniqueToken uniqueToken;

    DeleteAllowanceChecks subject;

    private final AccountID spender1 = asAccount("0.0.123");

    private final TokenID nftToken = asToken("0.0.200");
    private final AccountID payerId = asAccount("0.0.5000");
    private final AccountID ownerId = asAccount("0.0.5001");

    private Token nftModel = new Token(Id.fromGrpcToken(nftToken));

    private final NftRemoveAllowance nftAllowance1 = NftRemoveAllowance.newBuilder()
            .setOwner(ownerId)
            .setTokenId(nftToken)
            .addAllSerialNumbers(List.of(1L, 10L))
            .build();
    private final NftRemoveAllowance nftAllowance2 = NftRemoveAllowance.newBuilder()
            .setOwner(ownerId)
            .setTokenId(nftToken)
            .addAllSerialNumbers(List.of(20L))
            .build();

    private List<NftRemoveAllowance> nftAllowances = new ArrayList<>();

    private final Set<FcTokenAllowanceId> existingApproveForAllNftsAllowances = new TreeSet<>();

    final NftId nft1 = new NftId(0, 0, nftToken.getTokenNum(), 1L);

    private TransactionBody cryptoDeleteAllowanceTxn;
    private CryptoDeleteAllowanceTransactionBody op;

    @BeforeEach
    void setUp() {
        resetAllowances();
        nftModel = nftModel.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        nftAllowances.add(nftAllowance1);

        addExistingAllowancesAndSerials();

        subject = new DeleteAllowanceChecks();
    }

    private void addExistingAllowancesAndSerials() {
        existingApproveForAllNftsAllowances.add(
                FcTokenAllowanceId.from(EntityNum.fromTokenId(nftToken), EntityNum.fromAccountId(spender1)));
    }

    @Test
    void validateIfSerialsEmpty() {
        final List<Long> serials = List.of();
        var validity = subject.validateDeleteSerialNums(serials, nftModel, store);
        assertEquals(EMPTY_ALLOWANCES, validity);
    }

    @Test
    void semanticCheckForEmptyAllowancesInOp() {
        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder())
                .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
        assertEquals(EMPTY_ALLOWANCES, subject.validateAllowancesCount(op.getNftAllowancesList()));
    }

    @Test
    void rejectsMissingToken() {
        given(store.loadPossiblyPausedToken(asTypedEvmAddress(nftToken)))
                .willThrow(new InvalidTransactionException(INVALID_TOKEN_ID, true));
        nftAllowances.add(nftAllowance2);
        assertEquals(INVALID_TOKEN_ID, subject.validateNftDeleteAllowances(nftAllowances, payer, store));
    }

    @Test
    void validatesIfOwnerExists() {
        given(store.loadPossiblyPausedToken(asTypedEvmAddress(nftToken))).willReturn(nftModel);
        nftAllowances.add(nftAllowance2);
        given(store.getAccount(asTypedEvmAddress(ownerId), OnMissing.THROW))
                .willThrow(InvalidTransactionException.class);
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.validateNftDeleteAllowances(nftAllowances, payer, store));
    }

    @Test
    void considersPayerIfOwnerMissing() {
        final var allowance = NftRemoveAllowance.newBuilder().build();
        nftAllowances.add(allowance);
        assertEquals(
                Pair.of(payer, OK), subject.fetchOwnerAccount(Id.fromGrpcAccount(allowance.getOwner()), payer, store));
    }

    @Test
    void failsIfTokenNotAssociatedToAccount() {
        given(store.loadPossiblyPausedToken(asTypedEvmAddress(nftToken))).willReturn(nftModel);
        nftAllowances.add(nftAllowance2);
        given(store.getAccount(asTypedEvmAddress(ownerId), OnMissing.THROW)).willReturn(ownerAccount);
        given(store.hasAssociation(
                        new TokenRelationshipKey(nftModel.getId().asEvmAddress(), ownerAccount.getAccountAddress())))
                .willReturn(false);
        assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, subject.validateNftDeleteAllowances(nftAllowances, payer, store));
    }

    @Test
    void failsIfInvalidTypes() {
        nftAllowances.clear();

        nftModel = nftModel.setType(TokenType.FUNGIBLE_COMMON).setMaxSupply(5000L);
        given(store.loadPossiblyPausedToken(asTypedEvmAddress(nftToken))).willReturn(nftModel);
        nftAllowances.add(NftRemoveAllowance.newBuilder().setTokenId(nftToken).build());
        assertEquals(
                FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES, subject.validateNftDeleteAllowances(nftAllowances, payer, store));
    }

    @Test
    void returnsValidationOnceFailed() {

        for (int i = 0; i < 20; i++) {
            nftAllowances.add(nftAllowance1);
        }
        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder()
                        .addAllNftAllowances(nftAllowances)
                        .build())
                .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        assertEquals(
                MAX_ALLOWANCES_EXCEEDED, subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer, store));
    }

    @Test
    void succeedsWithEmptyLists() {
        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoDeleteAllowance(
                        CryptoDeleteAllowanceTransactionBody.newBuilder().build())
                .build();
        assertEquals(
                OK,
                subject.validateNftDeleteAllowances(
                        cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance().getNftAllowancesList(), payer, store));
    }

    @Test
    void happyPath() {
        setUpForTest();
        getValidTxnCtx();
        assertEquals(OK, subject.deleteAllowancesValidation(op.getNftAllowancesList(), payer, store));
    }

    @Test
    void validateSerialsExistence() {
        final var serials = List.of(1L, 10L);
        final NftId nftId1 = new NftId(nft1.shard(), nft1.realm(), nft1.num(), nft1.serialNo());
        given(store.getUniqueToken(nftId1, OnMissing.THROW)).willReturn(uniqueToken);

        var validity = subject.validateSerialNums(serials, nftModel, store);
        assertEquals(OK, validity);
    }

    @Test
    void returnsIfSerialsFail() {
        final var serials = List.of(1L, 10L);
        final NftId nftId1 = new NftId(nft1.shard(), nft1.realm(), nft1.num(), nft1.serialNo());
        given(store.getUniqueToken(nftId1, OnMissing.THROW)).willThrow(InvalidTransactionException.class);

        var validity = subject.validateSerialNums(serials, nftModel, store);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void addsSerialsCorrectly() {
        nftAllowances.add(nftAllowance1);
        nftAllowances.add(nftAllowance2);
        assertEquals(5, subject.aggregateNftDeleteAllowances(nftAllowances));
    }

    @Test
    void validatesNegativeSerialsAreNotValid() {
        final var serials = List.of(-100L, 10L);

        var validity = subject.validateSerialNums(serials, nftModel, store);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void validateSerials() {
        NftId nftId1 = new NftId(nft1.shard(), nft1.realm(), nft1.num(), 10L);
        NftId nftId2 = new NftId(nft1.shard(), nft1.realm(), nft1.num(), 1L);
        given(store.getUniqueToken(nftId1, OnMissing.THROW)).willReturn(uniqueToken);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken);

        var serials = List.of(1L, 10L, 1L);
        var validity = subject.validateSerialNums(serials, nftModel, store);
        assertEquals(OK, validity);

        serials = List.of(10L, 4L);
        nftId2 = new NftId(nft1.shard(), nft1.realm(), nft1.num(), 4L);
        given(store.getUniqueToken(nftId1, OnMissing.THROW)).willThrow(InvalidTransactionException.class);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken);
        validity = subject.validateSerialNums(serials, nftModel, store);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);

        nftId2 = new NftId(nft1.shard(), nft1.realm(), nft1.num(), 4L);
        nftId1 = new NftId(nft1.shard(), nft1.realm(), nft1.num(), 20L);
        given(store.getUniqueToken(nftId1, OnMissing.THROW)).willReturn(uniqueToken);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken);

        serials = List.of(20L, 4L);
        validity = subject.validateSerialNums(serials, nftModel, store);
        assertEquals(OK, validity);
    }

    private void getValidTxnCtx() {
        cryptoDeleteAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder()
                        .addAllNftAllowances(nftAllowances)
                        .build())
                .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payerId)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
                .build();
    }

    private void resetAllowances() {
        nftAllowances.clear();
    }

    private void setUpForTest() {
        given(store.loadPossiblyPausedToken(any())).willReturn(merkleToken);
        given(merkleToken.isFungibleCommon()).willReturn(false);
        given(merkleToken.getId()).willReturn(Id.fromGrpcToken(nftToken));
        given(store.hasAssociation(new TokenRelationshipKey(asTypedEvmAddress(nftToken), null)))
                .willReturn(true);
        given(payer.getId()).willReturn(Id.fromGrpcAccount(ownerId));
    }
}
