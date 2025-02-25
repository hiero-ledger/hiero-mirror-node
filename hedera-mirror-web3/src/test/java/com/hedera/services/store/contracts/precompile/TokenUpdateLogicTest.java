// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.node.app.service.evm.store.tokens.TokenType.FUNGIBLE_COMMON;
import static com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.store.tokens.HederaTokenStore.MISSING_TOKEN;
import static com.hedera.services.store.tokens.HederaTokenStore.asTokenRelationshipKey;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.google.protobuf.StringValue;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUpdateLogicTest {
    private static final long CONSENSUS_TIME = 1_234_567L;
    private static final TokenID fungible = IdUtils.asToken("0.0.888");
    private static final TokenID nonFungible = IdUtils.asToken("0.0.889");
    private static final AccountID accountId = IdUtils.asAccount("0.0.3");
    private static final AccountID treasury = IdUtils.asAccount("0.0.4");
    private static final NftId nftId =
            new NftId(nonFungible.getShardNum(), nonFungible.getRealmNum(), nonFungible.getTokenNum(), -1);
    private static final Timestamp EXPIRY = Timestamp.getDefaultInstance();

    @Mock
    private OptionValidator validator;

    @Mock
    private HederaTokenStore tokenStore;

    @Mock
    private Store store;

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private Token token;

    @Mock
    private Account account;

    @Mock
    private Account treasuryAccount;

    @Mock
    private TokenRelationship tokenRelationship;

    @Mock
    private TransactionBody transactionBody;

    private TokenUpdateLogic subject;
    private TokenUpdateTransactionBody op;

    @Test
    void callsWithInvalidTokenIdFail() {
        givenTokenUpdateLogic(true);
        op = TokenUpdateTransactionBody.newBuilder().setToken(MISSING_TOKEN).build();
        assertThrows(
                InvalidTransactionException.class, () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore));
    }

    @Test
    void callsWithoutAdminKeyFail() {
        givenTokenUpdateLogic(true);
        op = TokenUpdateTransactionBody.newBuilder().setToken(fungible).build();
        given(tokenStore.get(fungible)).willReturn(token);
        given(token.isDeleted()).willReturn(true);
        assertThrows(
                InvalidTransactionException.class, () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore));
    }

    @Test
    void callsWithInvalidExpiry() {
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, true);
        assertThrows(
                InvalidTransactionException.class, () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore));
    }

    @Test
    void updateTokenHappyPathForFungibleToken() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, true);
        givenContextForSuccessFullCalls();
        givenHederaStoreContextForFungible();
        givenKeys();
        given(token.getType()).willReturn(FUNGIBLE_COMMON);
        given(token.getTreasury()).willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(accountId), OnMissing.THROW)).willReturn(account);
        given(store.getTokenRelationship(any(), any())).willReturn(tokenRelationship);
        given(tokenRelationship.getBalance()).willReturn(100L);
        given(tokenStore.adjustBalance(treasury, fungible, -100L)).willReturn(OK);
        given(tokenStore.adjustBalance(accountId, fungible, 100L)).willReturn(OK);
        given(transactionBody.getTokenUpdate()).willReturn(op);
        // when
        subject.validate(transactionBody);
        subject.updateToken(op, CONSENSUS_TIME, store, tokenStore);
        // then
        verify(tokenStore).update(op, CONSENSUS_TIME);
    }

    @Test
    void updateTokenHappyPathForFungibleTokenWithoutTreasury() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        givenContextForSuccessFullCalls();
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.update(op, CONSENSUS_TIME)).willReturn(OK);
        given(transactionBody.getTokenUpdate()).willReturn(op);
        // when
        subject.validate(transactionBody);
        subject.updateToken(op, CONSENSUS_TIME, store, tokenStore);
        // then
        verify(tokenStore).update(op, CONSENSUS_TIME);
    }

    @Test
    void updateTokenHappyPathForFungibleTokenWithEmptyUpdateMemoButNonEmptyExisting() {
        final var memoToPreserve = "Should be preserved";
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        op = op.toBuilder().clearMemo().build();
        final var expectedOpToUseForUpdate =
                op.toBuilder().setMemo(StringValue.of(memoToPreserve)).build();
        givenContextForSuccessFullCalls();
        given(tokenStore.get(fungible)).willReturn(token);
        given(token.getMemo()).willReturn(memoToPreserve);
        given(tokenStore.update(expectedOpToUseForUpdate, CONSENSUS_TIME)).willReturn(OK);
        given(transactionBody.getTokenUpdate()).willReturn(op);
        // when
        subject.validate(transactionBody);
        subject.updateToken(op, CONSENSUS_TIME, true, store, tokenStore);
        // then
        verify(tokenStore).update(expectedOpToUseForUpdate, CONSENSUS_TIME);
    }

    @Test
    void updateTokenForFungibleTokenFailsWhenTransferringBetweenTreasuries() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, true);
        givenContextForSuccessFullCalls();
        givenHederaStoreContextForFungible();
        givenKeys();
        given(token.getType()).willReturn(FUNGIBLE_COMMON);
        given(token.getTreasury()).willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(accountId), OnMissing.THROW)).willReturn(account);
        given(tokenStore.adjustBalance(treasury, fungible, -100L)).willReturn(FAIL_INVALID);
        given(store.getTokenRelationship(any(), any())).willReturn(tokenRelationship);
        given(tokenRelationship.getBalance()).willReturn(100L);
        assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore),
                FAIL_INVALID.name());
    }

    @Test
    void updateTokenForFungibleTokenWithoutAdminKey() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, true);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(tokenStore.get(fungible)).willReturn(token);
        // then
        assertThrows(
                InvalidTransactionException.class, () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore));
    }

    @Test
    void updateTokenFailsWithAutoAssosiationErrorForFungibleToken() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, true);
        givenContextForSuccessFullCalls();
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.associationExists(any(), any())).willReturn(false);
        given(tokenStore.autoAssociate(any(), any())).willReturn(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
        // then
        assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore),
                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.name());
    }

    @Test
    void updateTokenFailsWithExistingTreasuryBalanceForNonFungibleToken() {
        // given
        givenTokenUpdateLogic(false);
        givenValidTransactionBody(false, true);
        givenContextForSuccessFullCalls();
        given(tokenStore.get(nonFungible)).willReturn(token);
        given(token.getTreasury()).willReturn(treasuryAccount);
        given(token.getType()).willReturn(NON_FUNGIBLE_UNIQUE);
        given(tokenStore.associationExists(any(), any())).willReturn(true);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, nonFungible), OnMissing.THROW))
                .willReturn(tokenRelationship);
        given(tokenRelationship.getBalance()).willReturn(10L);
        // then
        assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore),
                CURRENT_TREASURY_STILL_OWNS_NFTS.name());
    }

    @Test
    void updateTokenHappyPathWithNoTreasuryBalanceForNonFungibleToken() {
        // given
        givenTokenUpdateLogic(false);
        givenValidTransactionBody(false, true);
        givenContextForSuccessFullCalls();
        given(tokenStore.get(nonFungible)).willReturn(token);
        given(token.getTreasury()).willReturn(treasuryAccount);
        given(token.getType()).willReturn(NON_FUNGIBLE_UNIQUE);
        given(tokenStore.associationExists(any(), any())).willReturn(true);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(accountId), OnMissing.THROW)).willReturn(account);
        given(account.getNumTreasuryTitles()).willReturn(10);
        given(treasuryAccount.getNumTreasuryTitles()).willReturn(5);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, nonFungible), OnMissing.THROW))
                .willReturn(tokenRelationship);
        given(tokenRelationship.getBalance()).willReturn(0L);
        given(tokenStore.update(op, CONSENSUS_TIME)).willReturn(OK);
        given(transactionBody.getTokenUpdate()).willReturn(op);

        // when
        subject.validate(transactionBody);
        subject.updateToken(op, CONSENSUS_TIME, store, tokenStore);
        // then
        verify(tokenStore).update(op, CONSENSUS_TIME);
    }

    @Test
    void updateTokenFailsOnPrepTreasuryChangeForNonFungibleToken() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, true);
        givenContextForSuccessFullCalls();
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(tokenStore.get(fungible)).willReturn(token);
        given(token.getTreasury()).willReturn(treasuryAccount);
        given(token.hasFreezeKey()).willReturn(true);
        given(tokenStore.associationExists(any(), any())).willReturn(true);
        given(tokenStore.unfreeze(accountId, fungible)).willReturn(INVALID_ACCOUNT_ID);
        // then
        assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore),
                INVALID_ACCOUNT_ID.name());
    }

    @Test
    void updateTokenFailsWithInvalidFreezeKeyValue() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, true);
        givenContextForSuccessFullCalls();
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.autoAssociate(any(), any())).willReturn(OK);
        given(token.hasFreezeKey()).willReturn(true);
        given(tokenStore.unfreeze(any(), any())).willReturn(FAIL_INVALID);
        given(token.getTreasury()).willReturn(treasuryAccount);

        // then
        assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore),
                FAIL_INVALID.name());
    }

    @Test
    void updateTokenHappyPathForNonFungibleToken() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(false, true);
        givenContextForSuccessFullCalls();
        givenHederaStoreContextForNonFungible();
        given(token.getTreasury()).willReturn(treasuryAccount);
        given(transactionBody.getTokenUpdate()).willReturn(op);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(accountId), OnMissing.THROW)).willReturn(account);
        given(account.getNumTreasuryTitles()).willReturn(3);
        given(treasuryAccount.getNumTreasuryTitles()).willReturn(3);
        given(store.getTokenRelationship(any(), any())).willReturn(tokenRelationship);
        // when
        subject.validate(transactionBody);
        subject.updateToken(op, CONSENSUS_TIME, store, tokenStore);
        // then
        verify(tokenStore).update(op, CONSENSUS_TIME);
    }

    @Test
    void updateTokenFailsForNonFungibleTokenWithDetachedAutorenewAccount() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(false, true);
        givenContextForUnsuccessFullCalls();
        given(tokenStore.get(nonFungible)).willReturn(token);
        given(transactionBody.getTokenUpdate()).willReturn(op);
        // when
        subject.validate(transactionBody);
        // then

        assertThrows(
                InvalidTransactionException.class, () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore));
    }

    @Test
    void updateTokenHappyPathForNonFungibleTokenWithMissingAutorenewAccount() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(false, true);
        givenHederaStoreContextForNonFungible();
        given(token.hasAdminKey()).willReturn(true);
        given(validator.expiryStatusGiven(store, accountId)).willReturn(OK);
        given(validator.expiryStatusGiven(store, treasury)).willReturn(OK);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(token.hasAutoRenewAccount()).willReturn(false);
        given(token.getTreasury()).willReturn(treasuryAccount);
        given(treasuryAccount.getId()).willReturn(Id.fromGrpcAccount(treasury));
        given(token.getType()).willReturn(NON_FUNGIBLE_UNIQUE);
        given(tokenStore.get(nonFungible)).willReturn(token);
        given(transactionBody.getTokenUpdate()).willReturn(op);
        given(store.getTokenRelationship(any(), any())).willReturn(tokenRelationship);
        given(tokenRelationship.getBalance()).willReturn(100L);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(accountId), OnMissing.THROW)).willReturn(account);
        given(account.getNumTreasuryTitles()).willReturn(3);
        given(treasuryAccount.getNumTreasuryTitles()).willReturn(3);

        // when
        subject.validate(transactionBody);
        subject.updateToken(op, CONSENSUS_TIME, store, tokenStore);

        // then
        verify(tokenStore).update(op, CONSENSUS_TIME);
    }

    @Test
    void updateTokenKeysHappyPathForNonFungible() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(false, true);
        given(token.hasAdminKey()).willReturn(true);
        given(tokenStore.get(nonFungible)).willReturn(token);
        given(tokenStore.update(op, CONSENSUS_TIME)).willReturn(OK);
        given(transactionBody.getTokenUpdate()).willReturn(op);
        // when
        subject.validate(transactionBody);
        subject.updateTokenKeys(op, CONSENSUS_TIME, tokenStore);
        // then
        verify(tokenStore).update(op, CONSENSUS_TIME);
    }

    @Test
    void updateTokenKeysForNonFungibleFails() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(false, true);
        given(token.hasAdminKey()).willReturn(true);
        given(tokenStore.get(nonFungible)).willReturn(token);
        given(tokenStore.update(op, CONSENSUS_TIME)).willReturn(FAIL_INVALID);
        // then
        assertThrows(InvalidTransactionException.class, () -> subject.updateTokenKeys(op, CONSENSUS_TIME, tokenStore));
    }

    @Test
    void updateTokenForNonFungibleTokenFailsDueToWrongNftAllowance() {
        // given
        givenTokenUpdateLogic(false);
        givenValidTransactionBody(false, true);
        givenContextForSuccessFullCalls();
        given(tokenStore.get(nonFungible)).willReturn(token);
        given(tokenStore.autoAssociate(any(), any())).willReturn(OK);
        given(token.getType()).willReturn(NON_FUNGIBLE_UNIQUE);
        given(token.getTreasury()).willReturn(treasuryAccount);
        given(store.getTokenRelationship(any(), any())).willReturn(tokenRelationship);
        given(tokenRelationship.getBalance()).willReturn(100L);

        // then
        assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore),
                CURRENT_TREASURY_STILL_OWNS_NFTS.name());
    }

    @Test
    void updateTokenForNonFungibleTokenFailsDueToWrongNftAllowanceAndUnsufficientBalance() {
        // given
        givenTokenUpdateLogic(false);
        givenValidTransactionBody(false, true);
        givenContextForSuccessFullCalls();
        given(tokenStore.get(nonFungible)).willReturn(token);
        given(tokenStore.autoAssociate(any(), any())).willReturn(OK);
        given(tokenStore.update(op, CONSENSUS_TIME)).willReturn(FAIL_INVALID);
        given(token.getType()).willReturn(NON_FUNGIBLE_UNIQUE);
        given(token.getTreasury()).willReturn(account);
        given(account.getId()).willReturn(Id.fromGrpcAccount(accountId));
        given(store.getTokenRelationship(any(), any())).willReturn(tokenRelationship);
        given(tokenRelationship.getBalance()).willReturn(-1L);
        // then
        assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME, store, tokenStore),
                FAIL_INVALID.name());
    }

    @Test
    void updateTokenExpiryInfoHappyPath() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        givenContextForSuccessFullCalls();
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.resolve(op.getToken())).willReturn(op.getToken());
        given(tokenStore.updateExpiryInfo(op)).willReturn(OK);
        // when
        subject.updateTokenExpiryInfo(op, store, tokenStore);
        // then
        verify(tokenStore).updateExpiryInfo(op);
    }

    @Test
    void updateTokenExpiryInfoFailsForInvalidExpirationTime() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        givenContextForSuccessFullCalls();
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.resolve(op.getToken())).willReturn(op.getToken());
        given(tokenStore.updateExpiryInfo(op)).willReturn(INVALID_EXPIRATION_TIME);

        assertThrows(InvalidTransactionException.class, () -> subject.updateTokenExpiryInfo(op, store, tokenStore));
    }

    @Test
    void updateTokenExpiryInfoFailsForExpiredAccount() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        given(token.hasAdminKey()).willReturn(true);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(validator.expiryStatusGiven(store, accountId)).willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.resolve(op.getToken())).willReturn(op.getToken());

        assertFailsWith(
                () -> subject.updateTokenExpiryInfo(op, store, tokenStore), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    }

    @Test
    void updateTokenExpiryInfoFailsForMissingAutoRenewAccount() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        given(token.hasAdminKey()).willReturn(true);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.resolve(op.getToken())).willReturn(op.getToken());
        given(store.getAccount(asTypedEvmAddress(accountId), OnMissing.DONT_THROW))
                .willReturn(account);
        given(account.isEmptyAccount()).willReturn(true);

        assertFailsWith(() -> subject.updateTokenExpiryInfo(op, store, tokenStore), INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void updateTokenExpiryInfoForEmptyExpiry() {
        // given
        final var txnBodyWithEmptyExpiry = TokenUpdateTransactionBody.newBuilder()
                .setToken(fungible)
                .setName("name")
                .setMemo(StringValue.of("memo"))
                .setSymbol("symbol")
                .setTreasury(accountId)
                .setAutoRenewAccount(accountId)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(2L))
                .build();
        givenTokenUpdateLogic(true);
        given(token.hasAdminKey()).willReturn(true);
        given(token.hasAutoRenewAccount()).willReturn(true);
        given(token.getAutoRenewAccount()).willReturn(treasuryAccount);
        given(validator.expiryStatusGiven(store, accountId)).willReturn(OK);
        given(validator.expiryStatusGiven(store, treasury)).willReturn(OK);
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.resolve(txnBodyWithEmptyExpiry.getToken())).willReturn(txnBodyWithEmptyExpiry.getToken());
        given(tokenStore.updateExpiryInfo(txnBodyWithEmptyExpiry)).willReturn(OK);
        given(treasuryAccount.getId()).willReturn(Id.fromGrpcAccount(treasury));

        // when
        subject.updateTokenExpiryInfo(txnBodyWithEmptyExpiry, store, tokenStore);
        // then
        verify(tokenStore).updateExpiryInfo(txnBodyWithEmptyExpiry);
    }

    @Test
    void updateTokenExpiryInfoFailsForMissingToken() {
        // given
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        given(tokenStore.resolve(op.getToken())).willReturn(MISSING_TOKEN);

        assertThrows(InvalidTransactionException.class, () -> subject.updateTokenExpiryInfo(op, store, tokenStore));
    }

    @Test
    void updateTokenExpiryInfoFailsForDeletedToken() {
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        given(token.hasAdminKey()).willReturn(true);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.resolve(op.getToken())).willReturn(op.getToken());
        given(token.isDeleted()).willReturn(true);
        assertThrows(InvalidTransactionException.class, () -> subject.updateTokenExpiryInfo(op, store, tokenStore));
    }

    @Test
    void updateTokenExpiryInfoFailsForPausedToken() {
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        given(token.hasAdminKey()).willReturn(true);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.resolve(op.getToken())).willReturn(op.getToken());
        given(token.isDeleted()).willReturn(false);
        given(token.isPaused()).willReturn(true);
        assertThrows(InvalidTransactionException.class, () -> subject.updateTokenExpiryInfo(op, store, tokenStore));
    }

    @Test
    void updateTokenExpiryInfoFailsForMissingAdminKey() {
        givenTokenUpdateLogic(true);
        givenValidTransactionBody(true, false);
        given(token.hasAdminKey()).willReturn(false);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.resolve(op.getToken())).willReturn(op.getToken());
        assertThrows(InvalidTransactionException.class, () -> subject.updateTokenExpiryInfo(op, store, tokenStore));
    }

    private void givenContextForSuccessFullCalls() {
        given(token.hasAdminKey()).willReturn(true);
        lenient().when(token.getAutoRenewAccount()).thenReturn(account);
        lenient().when(treasuryAccount.getId()).thenReturn(Id.fromGrpcAccount(treasury));
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(validator.expiryStatusGiven(store, accountId)).willReturn(OK);
        lenient().when(validator.expiryStatusGiven(store, treasury)).thenReturn(OK);
    }

    private void givenContextForUnsuccessFullCalls() {
        given(token.hasAdminKey()).willReturn(true);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(validator.expiryStatusGiven(store, accountId)).willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    }

    private void givenHederaStoreContextForFungible() {
        given(tokenStore.get(fungible)).willReturn(token);
        given(tokenStore.autoAssociate(any(), any())).willReturn(OK);
        given(tokenStore.update(op, CONSENSUS_TIME)).willReturn(OK);
    }

    private void givenHederaStoreContextForNonFungible() {
        given(tokenStore.get(nonFungible)).willReturn(token);
        given(tokenStore.autoAssociate(any(), any())).willReturn(OK);
        given(tokenStore.update(op, CONSENSUS_TIME)).willReturn(OK);
        lenient()
                .when(tokenStore.changeOwnerWildCard(nftId, treasury, accountId))
                .thenReturn(OK);
    }

    private void givenValidTransactionBody(boolean isFungible, boolean hasTreasury) {
        var builder = TokenUpdateTransactionBody.newBuilder();
        builder = isFungible
                ? builder.setToken(fungible)
                        .setName("name")
                        .setMemo(StringValue.of("memo"))
                        .setSymbol("symbol")
                        .setExpiry(EXPIRY)
                        .setAutoRenewAccount(accountId)
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(2L))
                : builder.setToken(nonFungible)
                        .setName("NFT")
                        .setMemo(StringValue.of("NftMemo"))
                        .setSymbol("NftSymbol")
                        .setExpiry(EXPIRY)
                        .setAutoRenewAccount(accountId)
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(2L));

        if (hasTreasury) {
            builder.setTreasury(accountId);
        }
        op = builder.build();
    }

    private void givenKeys() {
        given(token.hasFreezeKey()).willReturn(true);
        given(token.hasKycKey()).willReturn(true);
        given(tokenStore.unfreeze(any(), any())).willReturn(OK);
        given(tokenStore.grantKyc(any(), any())).willReturn(OK);
    }

    private void givenTokenUpdateLogic(boolean hasValidNftAllowance) {
        lenient()
                .when(store.getAccount(asTypedEvmAddress(accountId), OnMissing.DONT_THROW))
                .thenReturn(account);
        lenient().when(evmProperties.isAllowTreasuryToOwnNfts()).thenReturn(hasValidNftAllowance);
        subject = new TokenUpdateLogic(evmProperties, validator);
    }
}
