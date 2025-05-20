// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.tokens;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.jproto.JKey;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import java.security.InvalidKeyException;
import java.util.EnumSet;
import java.util.function.UnaryOperator;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.StoreImpl;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaTokenStoreTest {
    private static final Key KEY = Key.newBuilder()
            .setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .build();
    private static final Key NEW_KEY = Key.newBuilder()
            .setEd25519(ByteString.copyFromUtf8("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
            .build();

    private final JKey kycKey = asFcKeyUnchecked(
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
    private static final int ASSOCIATED_TOKENS_COUNT = 2;
    private static final int NUM_POSITIVE_BALANCES = 1;
    public static final long CONSENSUS_NOW = 1_234_567L;
    private static final long EXPIRY = CONSENSUS_NOW + 1_234_567L;
    private static final long NEW_EXPIRY = CONSENSUS_NOW + 1_432_765L;
    private static final long TREASURY_BALANCE = 50_000L;
    private static final TokenID MISC = asToken("0.0.1");
    private static final TokenID NONFUNGIBLE = asToken("0.0.2");
    private static final int MAX_AUTO_ASSOCIATIONS = 1234;
    private static final AccountID PAYER = IdUtils.asAccount("0.0.12345");
    private static final AccountID PRIMARY_TREASURY = IdUtils.asAccount("0.0.9898");
    private static final AccountID TREASURY = IdUtils.asAccount("0.0.3");
    private static final AccountID NEW_AUTO_RENEW_ACCOUNT = IdUtils.asAccount("0.0.6");
    private static final AccountID SPONSOR = IdUtils.asAccount("0.0.666");
    private static final AccountID COUNTERPARTY = IdUtils.asAccount("0.0.777");
    private static final AccountID ANOTHER_FEE_COLLECTOR = IdUtils.asAccount("0.0.777");
    private static final TokenRelationshipKey SPONSOR_NFT = asTokenRelationshipKey(SPONSOR, NONFUNGIBLE);
    private static final TokenRelationshipKey COUNTERPARTY_NFT = asTokenRelationshipKey(COUNTERPARTY, NONFUNGIBLE);
    private static final NftId A_NFT = new NftId(0, 0, 2, 1234);
    private static final NftId T_NFT = new NftId(0, 0, 2, 12345);
    private static final String NEW_SYMBOL = "REALLYSOM";
    private static final String NEW_MEMO = "NEWMEMO";
    private static final String NEW_NAME = "NEWNAME";
    private static final AccountID NEW_TREASURY = IdUtils.asAccount("0.0.1");
    private static final long NEW_AUTO_RENEW_PERIOD = 200_000L;
    private static final EnumSet<KeyType> NO_KEYS = EnumSet.noneOf(KeyType.class);
    private static final EnumSet<KeyType> ALL_KEYS = EnumSet.complementOf(EnumSet.of(KeyType.EMPTY_ADMIN));
    private StoreImpl store;
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private HederaTokenStore subject;

    @Mock
    private UnaryOperator<Token> change;

    private static TokenRelationshipKey asTokenRelationshipKey(AccountID accountID, TokenID tokenID) {
        return new TokenRelationshipKey(asTypedEvmAddress(tokenID), asTypedEvmAddress(accountID));
    }

    @BeforeEach
    void setup() {
        mirrorNodeEvmProperties = mock(MirrorNodeEvmProperties.class);
        store = mock(StoreImpl.class);
        var validator = new ContextOptionValidator(mirrorNodeEvmProperties);
        subject = new HederaTokenStore(validator, mirrorNodeEvmProperties, store);
    }

    @Test
    void getThrowsIseOnMissing() {
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());

        assertThrows(IllegalArgumentException.class, () -> subject.get(MISC));
    }

    @Test
    void existenceCheckUnderstandsPendingIdOnlyAppliesIfCreationPending() {
        given(store.getToken(asTypedEvmAddress(HederaTokenStore.NO_PENDING_ID), OnMissing.DONT_THROW))
                .willReturn(Token.getEmptyToken());

        assertFalse(subject.exists(HederaTokenStore.NO_PENDING_ID));
    }

    @Test
    void associatingRejectsDeletedTokens() {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0);
        var token = new Token(Id.fromGrpcToken(MISC)).setIsDeleted(true);

        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.autoAssociate(SPONSOR, MISC);

        assertEquals(TOKEN_WAS_DELETED, status);
    }

    @Test
    void associatingRejectsMissingToken() {
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW))
                .willReturn(new Account(0L, Id.fromGrpcAccount(SPONSOR), 0));

        final var status = subject.autoAssociate(SPONSOR, MISC);

        assertEquals(INVALID_TOKEN_ID, status);
    }

    @Test
    void associatingRejectsMissingAccounts() {
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willThrow(new MissingEntityException(""));

        final var status = subject.autoAssociate(SPONSOR, MISC);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void associatingRejectsAlreadyAssociatedTokens() {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0);
        var token = new Token(Id.fromGrpcToken(MISC));

        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, MISC), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, account));
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.autoAssociate(SPONSOR, MISC);

        assertEquals(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void cannotAutoAssociateIfAccountReachedTokenAssociationLimit() {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0);
        var token = new Token(Id.fromGrpcToken(MISC));

        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, MISC), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW))
                .willReturn(account.setNumAssociations(ASSOCIATED_TOKENS_COUNT));
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(mirrorNodeEvmProperties.isLimitTokenAssociations()).willReturn(true);
        given(mirrorNodeEvmProperties.getMaxTokensPerAccount()).willReturn(ASSOCIATED_TOKENS_COUNT);

        final var status = subject.autoAssociate(SPONSOR, MISC);

        assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
    }

    @Test
    void associatingFailsWhenAutoAssociationLimitReached() {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setMaxAutoAssociations(MAX_AUTO_ASSOCIATIONS)
                .setUsedAutoAssociations(MAX_AUTO_ASSOCIATIONS);
        var token = new Token(Id.fromGrpcToken(MISC));

        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, MISC), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);

        var status = subject.autoAssociate(SPONSOR, MISC);
        assertEquals(NO_REMAINING_AUTOMATIC_ASSOCIATIONS, status);
    }

    @Test
    void adjustingRejectsMissingAccount() {
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var status = subject.adjustBalance(SPONSOR, MISC, 1);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingSender() {
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var status = subject.changeOwner(A_NFT, SPONSOR, COUNTERPARTY);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingReceiver() {
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var status = subject.changeOwner(A_NFT, SPONSOR, COUNTERPARTY);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingNftInstance() {
        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0);
        var token = new Token(Id.fromGrpcToken(A_NFT.tokenId()));

        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, sponsorAccount));
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, counterpartyAccount));
        given(store.getUniqueToken(A_NFT, OnMissing.DONT_THROW)).willReturn(UniqueToken.getEmptyUniqueToken());

        final var status = subject.changeOwner(A_NFT, SPONSOR, COUNTERPARTY);

        assertEquals(INVALID_NFT_ID, status);
    }

    @Test
    void changingOwnerRejectsUnassociatedReceiver() {
        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0);
        var token = new Token(Id.fromGrpcToken(A_NFT.tokenId())).setTreasury(sponsorAccount);

        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());

        final var status = subject.changeOwner(A_NFT, SPONSOR, COUNTERPARTY);

        assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void changingOwnerAutoAssociatesCounterpartyWithOpenSlots() {
        final long startSponsorNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startSponsorANfts = 1;
        final long startCounterpartyANfts = 0;

        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0)
                .setMaxAutoAssociations(100)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES)
                .setOwnedNfts(startCounterpartyNfts);
        final var updated1CounterpartyAccount = counterpartyAccount
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT + 1)
                .setUsedAutoAssociations(1);
        final var updated2CounterpartyAccount = updated1CounterpartyAccount
                .setOwnedNfts(updated1CounterpartyAccount.getOwnedNfts() + 1)
                .setNumPositiveBalances(updated1CounterpartyAccount.getNumPositiveBalances() + 1);

        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0)
                .setOwnedNfts(startSponsorNfts)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES);

        var token = new Token(Id.fromGrpcToken(NONFUNGIBLE)).setTreasury(sponsorAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(NONFUNGIBLE),
                1234,
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(SPONSOR),
                Id.DEFAULT,
                new byte[0]);

        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setBalance(startCounterpartyANfts);
        var sponsorRel = new TokenRelationship(token, sponsorAccount).setBalance(startSponsorANfts);

        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        updated1CounterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);

        given(store.getToken(asTypedEvmAddress(NONFUNGIBLE), OnMissing.THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(NONFUNGIBLE), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);
        given(store.getUniqueToken(nft.getNftId(), OnMissing.THROW)).willReturn(nft);

        given(store.getTokenRelationship(COUNTERPARTY_NFT, OnMissing.THROW)).willReturn(counterpartyRel);
        given(store.getTokenRelationship(COUNTERPARTY_NFT, OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());
        given(store.getTokenRelationship(SPONSOR_NFT, OnMissing.THROW)).willReturn(sponsorRel);
        given(store.getTokenRelationship(SPONSOR_NFT, OnMissing.DONT_THROW)).willReturn(sponsorRel);

        final var status = subject.changeOwner(A_NFT, SPONSOR, COUNTERPARTY);

        assertEquals(OK, status);
        verify(store).updateAccount(updated1CounterpartyAccount);
        verify(store).updateAccount(updated2CounterpartyAccount);
    }

    @Test
    void changingOwnerRejectsIllegitimateOwner() {
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0);
        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0);
        var token = new Token(Id.fromGrpcToken(A_NFT.tokenId())).setTreasury(sponsorAccount);

        // Set the owner to `counterparty` instead of `sponsor`
        var nft = new UniqueToken(
                Id.fromGrpcToken(A_NFT.tokenId()),
                1234,
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(COUNTERPARTY),
                Id.DEFAULT,
                new byte[0]);

        var counterpartyRel = new TokenRelationship(token, counterpartyAccount);
        var sponsorRel = new TokenRelationship(token, sponsorAccount);

        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);

        given(store.getToken(asTypedEvmAddress(A_NFT.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, A_NFT.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, A_NFT.tokenId()), OnMissing.THROW))
                .willReturn(sponsorRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(sponsorRel);

        final var status = subject.changeOwner(A_NFT, SPONSOR, COUNTERPARTY);

        assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, status);
    }

    @Test
    void changingOwnerDoesTheExpected() {
        final long startSponsorNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startSponsorANfts = 4;
        final long startCounterpartyANfts = 1;

        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0)
                .setOwnedNfts(startSponsorNfts)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0)
                .setOwnedNfts(startCounterpartyNfts)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES);
        var token = new Token(Id.fromGrpcToken(A_NFT.tokenId())).setTreasury(sponsorAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(A_NFT.tokenId()),
                A_NFT.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(SPONSOR),
                Id.DEFAULT,
                new byte[0]);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setBalance(startCounterpartyANfts);
        var sponsorRel = new TokenRelationship(token, sponsorAccount).setBalance(startSponsorANfts);

        var updatedNft = nft.setOwner(Id.fromGrpcAccount(COUNTERPARTY));
        var updatedSponsorAccount = sponsorAccount.setOwnedNfts(startSponsorNfts - 1);
        var updatedCounterpartyAccount = counterpartyAccount.setOwnedNfts(startCounterpartyNfts + 1);
        var updatedSponsorRel = sponsorRel.setBalance(startSponsorANfts - 1);
        var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyANfts + 1);

        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);

        given(store.getToken(asTypedEvmAddress(A_NFT.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);
        given(store.getUniqueToken(nft.getNftId(), OnMissing.THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, A_NFT.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, A_NFT.tokenId()), OnMissing.THROW))
                .willReturn(sponsorRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, A_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(sponsorRel);

        final var status = subject.changeOwner(A_NFT, SPONSOR, COUNTERPARTY);

        assertEquals(OK, status);
        verify(store).updateUniqueToken(updatedNft);
        verify(store).updateAccount(updatedSponsorAccount);
        verify(store).updateAccount(updatedCounterpartyAccount);
        verify(store).updateTokenRelationship(updatedSponsorRel);
        verify(store).updateTokenRelationship(updatedCounterpartyRel);
    }

    @Test
    void changingOwnerDoesTheExpectedWithTreasuryReturn() {
        final long startTreasuryNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startTreasuryTNfts = 4;
        final long startCounterpartyTNfts = 1;

        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(PRIMARY_TREASURY), 0)
                .setOwnedNfts(startTreasuryNfts)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0)
                .setOwnedNfts(startCounterpartyNfts)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES);
        var token = new Token(Id.fromGrpcToken(T_NFT.tokenId())).setTreasury(treasuryAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(T_NFT.tokenId()),
                T_NFT.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(COUNTERPARTY),
                Id.DEFAULT,
                new byte[0]);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setBalance(startCounterpartyTNfts);
        var treasuryRel = new TokenRelationship(token, treasuryAccount).setBalance(startTreasuryTNfts);

        var updatedNft = nft.setOwner(Id.DEFAULT);
        var updatedTreasuryAccount = treasuryAccount.setOwnedNfts(startTreasuryNfts + 1);
        var updatedCounterpartyAccount = counterpartyAccount
                .setOwnedNfts(startCounterpartyNfts - 1)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES - 1);
        var updatedTreasuryRel = treasuryRel.setBalance(startTreasuryTNfts + 1);
        var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyTNfts - 1);

        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);

        given(store.getToken(asTypedEvmAddress(T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);
        given(store.getUniqueToken(nft.getNftId(), OnMissing.THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(
                        asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwner(T_NFT, COUNTERPARTY, PRIMARY_TREASURY);

        assertEquals(OK, status);
        verify(store).updateUniqueToken(updatedNft);
        verify(store).updateAccount(updatedTreasuryAccount);
        verify(store).updateAccount(updatedCounterpartyAccount);
        verify(store).updateTokenRelationship(updatedTreasuryRel);
        verify(store).updateTokenRelationship(updatedCounterpartyRel);
    }

    @Test
    void changingOwnerDoesTheExpectedWithTreasuryExit() {
        final long startTreasuryNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startTreasuryTNfts = 4;
        final long startCounterpartyTNfts = 1;

        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(PRIMARY_TREASURY), 0)
                .setOwnedNfts(startTreasuryNfts)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0)
                .setOwnedNfts(startCounterpartyNfts)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES);
        var token = new Token(Id.fromGrpcToken(T_NFT.tokenId())).setTreasury(treasuryAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(T_NFT.tokenId()),
                T_NFT.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(PRIMARY_TREASURY),
                Id.DEFAULT,
                new byte[0]);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setBalance(startCounterpartyTNfts);
        var treasuryRel = new TokenRelationship(token, treasuryAccount).setBalance(startTreasuryTNfts);

        var updatedNft = nft.setOwner(Id.fromGrpcAccount(COUNTERPARTY));
        var updatedTreasuryAccount = treasuryAccount.setOwnedNfts(startTreasuryNfts - 1);
        var updatedCounterpartyAccount = counterpartyAccount.setOwnedNfts(startCounterpartyNfts + 1);
        var updatedTreasuryRel = treasuryRel.setBalance(startTreasuryTNfts - 1);
        var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyTNfts + 1);

        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);

        given(store.getToken(asTypedEvmAddress(T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);
        given(store.getUniqueToken(nft.getNftId(), OnMissing.THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(
                        asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwner(T_NFT, PRIMARY_TREASURY, COUNTERPARTY);

        assertEquals(OK, status);

        verify(store).updateUniqueToken(updatedNft);
        verify(store).updateAccount(updatedTreasuryAccount);
        verify(store).updateAccount(updatedCounterpartyAccount);
        verify(store).updateTokenRelationship(updatedTreasuryRel);
        verify(store).updateTokenRelationship(updatedCounterpartyRel);
    }

    @Test
    void changingOwnerRejectsFromFreezeAndKYC() {
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(PRIMARY_TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(T_NFT.tokenId())).setTreasury(treasuryAccount);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount);
        var treasuryRel = new TokenRelationship(token, treasuryAccount).setFrozen(true);
        var nft = new UniqueToken(
                Id.fromGrpcToken(T_NFT.tokenId()),
                T_NFT.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(COUNTERPARTY),
                Id.DEFAULT,
                new byte[0]);

        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);

        given(store.getToken(asTypedEvmAddress(T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(
                        asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwner(T_NFT, PRIMARY_TREASURY, COUNTERPARTY);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void changingOwnerRejectsToFreezeAndKYC() {
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(PRIMARY_TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(T_NFT.tokenId())).setTreasury(treasuryAccount);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setFrozen(true);
        var treasuryRel = new TokenRelationship(token, treasuryAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(T_NFT.tokenId()),
                T_NFT.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(COUNTERPARTY),
                Id.DEFAULT,
                new byte[0]);

        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);

        given(store.getToken(asTypedEvmAddress(T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(
                        asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwner(T_NFT, PRIMARY_TREASURY, COUNTERPARTY);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void adjustingRejectsMissingToken() {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0);

        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());

        final var status = subject.adjustBalance(SPONSOR, MISC, 1);

        assertEquals(INVALID_TOKEN_ID, status);
    }

    @Test
    void adjustingRejectsDeletedToken() {
        var account = new Account(0L, Id.fromGrpcAccount(TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(MISC)).setIsDeleted(true);

        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.adjustBalance(TREASURY, MISC, 1);

        assertEquals(TOKEN_WAS_DELETED, status);
    }

    @Test
    void adjustingRejectsPausedToken() throws InvalidKeyException {
        var account = new Account(0L, Id.fromGrpcAccount(TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(MISC)).setPauseKey(JKey.mapKey(KEY));
        token = token.changePauseStatus(true);

        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.adjustBalance(TREASURY, MISC, 1);

        assertEquals(TOKEN_IS_PAUSED, status);
    }

    @Test
    void adjustingRejectsFungibleUniqueToken() {
        var account = new Account(0L, Id.fromGrpcAccount(TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(MISC)).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.adjustBalance(TREASURY, MISC, 1);

        assertEquals(ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON, status);
    }

    @Test
    void refusesToAdjustFrozenRelationship() {
        var account = new Account(0L, Id.fromGrpcAccount(TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(MISC));
        var tokenRelationship = new TokenRelationship(token, account).setFrozen(true);

        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(TREASURY, MISC, -1);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void refusesToAdjustRevokedKycRelationship() {
        var account = new Account(0L, Id.fromGrpcAccount(TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(MISC)).setKycKey(kycKey);
        var tokenRelationship = new TokenRelationship(token, account).changeKycState(false);

        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(TREASURY, MISC, -1);

        assertEquals(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN, status);
    }

    @Test
    void refusesInvalidAdjustment() {
        var account = new Account(0L, Id.fromGrpcAccount(TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(MISC));
        var tokenRelationship = new TokenRelationship(token, account);

        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(TREASURY, MISC, -TREASURY_BALANCE - 1);

        assertEquals(INSUFFICIENT_TOKEN_BALANCE, status);
    }

    @Test
    void adjustmentFailsOnAutomaticAssociationLimitNotSet() {
        var account = new Account(0L, Id.fromGrpcAccount(ANOTHER_FEE_COLLECTOR), 0);
        var token = new Token(Id.fromGrpcToken(MISC));

        given(store.getAccount(asTypedEvmAddress(ANOTHER_FEE_COLLECTOR), OnMissing.THROW))
                .willReturn(account);
        given(store.getAccount(asTypedEvmAddress(ANOTHER_FEE_COLLECTOR), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(ANOTHER_FEE_COLLECTOR, MISC), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());

        final var status = subject.adjustBalance(ANOTHER_FEE_COLLECTOR, MISC, -1);
        assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void adjustmentFailsOnAutomaticAssociationLimitReached() {
        var account = new Account(0L, Id.fromGrpcAccount(ANOTHER_FEE_COLLECTOR), 0)
                .setMaxAutoAssociations(3)
                .setNumAssociations(3)
                .setUsedAutoAssociations(3);
        var token = new Token(Id.fromGrpcToken(MISC));

        given(store.getAccount(asTypedEvmAddress(ANOTHER_FEE_COLLECTOR), OnMissing.THROW))
                .willReturn(account);
        given(store.getAccount(asTypedEvmAddress(ANOTHER_FEE_COLLECTOR), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(ANOTHER_FEE_COLLECTOR, MISC), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());

        final var status = subject.adjustBalance(ANOTHER_FEE_COLLECTOR, MISC, 1);

        assertEquals(NO_REMAINING_AUTOMATIC_ASSOCIATIONS, status);
        verify(store, never()).updateTokenRelationship(any());
        verify(store, never()).updateAccount(any());
    }

    @Test
    void adjustmentWorksAndIncrementsAlreadyUsedAutoAssociationCountForNewAssociation() {
        var account = new Account(0L, Id.fromGrpcAccount(ANOTHER_FEE_COLLECTOR), 0)
                .setMaxAutoAssociations(5)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES)
                .setUsedAutoAssociations(3);
        var token = new Token(Id.fromGrpcToken(MISC));
        var tokenRelationship = new TokenRelationship(token, account)
                .setFrozen(false)
                .setKycGranted(true)
                .setBalance(0);

        var updatedTokenRelationship = tokenRelationship.setBalance(1);
        var updatedAccount1 =
                account.setNumAssociations(ASSOCIATED_TOKENS_COUNT + 1).setUsedAutoAssociations(4);
        var updatedAccount2 = updatedAccount1.setNumPositiveBalances(NUM_POSITIVE_BALANCES + 1);

        given(store.getAccount(asTypedEvmAddress(ANOTHER_FEE_COLLECTOR), OnMissing.THROW))
                .willReturn(account, account, account, account, account, updatedAccount1);
        given(store.getAccount(asTypedEvmAddress(ANOTHER_FEE_COLLECTOR), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(ANOTHER_FEE_COLLECTOR, MISC), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());
        given(store.getTokenRelationship(asTokenRelationshipKey(ANOTHER_FEE_COLLECTOR, MISC), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(ANOTHER_FEE_COLLECTOR, MISC, 1);

        assertEquals(OK, status);
        verify(store).updateTokenRelationship(updatedTokenRelationship);
        verify(store).updateAccount(updatedAccount1);
        verify(store).updateAccount(updatedAccount2);
    }

    @Test
    void performsValidAdjustment() {
        var account = new Account(0L, Id.fromGrpcAccount(TREASURY), 0)
                .setNumAssociations(ASSOCIATED_TOKENS_COUNT)
                .setNumPositiveBalances(NUM_POSITIVE_BALANCES);
        var token = new Token(Id.fromGrpcToken(MISC));
        var tokenRelationship = new TokenRelationship(token, account).setBalance(1);

        var updatedAccount = account.setNumPositiveBalances(NUM_POSITIVE_BALANCES - 1);
        var updatedTokenRelationship = tokenRelationship.setBalance(0);

        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.THROW))
                .willReturn(tokenRelationship);

        subject.adjustBalance(TREASURY, MISC, -1);

        verify(store).updateTokenRelationship(updatedTokenRelationship);
        verify(store).updateAccount(updatedAccount);
    }

    @Test
    void adaptsBehaviorToFungibleType() {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0)
                .setNumAssociations(5)
                .setNumPositiveBalances(2);
        var token = new Token(Id.fromGrpcToken(MISC)).setDecimals(2);
        token = token.setKycKey(kycKey);
        var tokenRelationship =
                new TokenRelationship(token, account).setFrozen(false).changeKycState(true);

        final var aa =
                AccountAmount.newBuilder().setAccountID(SPONSOR).setAmount(100).build();
        final var fungibleChange = BalanceChange.changingFtUnits(Id.fromGrpcToken(MISC), MISC, aa, PAYER);
        fungibleChange.setExpectedDecimals(2);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, MISC), OnMissing.THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, MISC), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);

        assertEquals(2, subject.get(MISC).getDecimals());
        assertEquals(2, fungibleChange.getExpectedDecimals());

        final var result = subject.tryTokenChange(fungibleChange);
        assertEquals(OK, result);
    }

    @Test
    void failsIfMismatchingDecimals() {
        var token = new Token(Id.fromGrpcToken(MISC)).setDecimals(2);
        final var aa =
                AccountAmount.newBuilder().setAccountID(SPONSOR).setAmount(100).build();
        final var fungibleChange = BalanceChange.changingFtUnits(Id.fromGrpcToken(MISC), MISC, aa, PAYER);
        assertFalse(fungibleChange.hasExpectedDecimals());

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);

        fungibleChange.setExpectedDecimals(4);

        assertEquals(2, subject.get(MISC).getDecimals());
        assertEquals(4, fungibleChange.getExpectedDecimals());

        final var result = subject.tryTokenChange(fungibleChange);
        assertEquals(UNEXPECTED_TOKEN_DECIMALS, result);
    }

    @Test
    void decimalMatchingWorks() {
        var token = new Token(Id.fromGrpcToken(MISC)).setDecimals(2);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);

        assertEquals(2, subject.get(MISC).getDecimals());
        assertTrue(subject.matchesTokenDecimals(MISC, 2));
        assertFalse(subject.matchesTokenDecimals(MISC, 4));
    }

    @Test
    void realAssociationsExist() {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0);
        var token = new Token(Id.fromGrpcToken(MISC));
        var tokenRelationship = new TokenRelationship(token, account);

        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(SPONSOR, MISC), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        assertTrue(subject.associationExists(SPONSOR, MISC));
    }

    @Test
    void noAssociationsWithMissingAccounts() {
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        assertFalse(subject.associationExists(SPONSOR, MISC));
    }

    @Test
    void applicationRejectsMissing() {
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willThrow(InvalidTransactionException.class);

        assertThrows(InvalidTransactionException.class, () -> subject.apply(MISC, change));
    }

    @Test
    void applicationAlwaysReplacesModifiableToken() {
        var token = new Token(Id.fromGrpcToken(MISC));

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);
        willThrow(IllegalStateException.class).given(change).apply(token);

        assertThrows(IllegalArgumentException.class, () -> subject.apply(MISC, change));
    }

    @Test
    void grantingKycRejectsMissingAccount() {
        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var status = subject.grantKyc(SPONSOR, MISC);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void grantingKycRejectsDetachedAccount() {
        final var detachedSponsorId =
                AccountID.newBuilder().setAccountNum(666_666).build();
        var detachedSponsor = new Account(0L, Id.fromGrpcAccount(detachedSponsorId), 0);

        given(mirrorNodeEvmProperties.shouldAutoRenewSomeEntityType()).willReturn(true);
        given(mirrorNodeEvmProperties.shouldAutoRenewAccounts()).willReturn(true);
        given(store.getAccount(asTypedEvmAddress(detachedSponsorId), OnMissing.DONT_THROW))
                .willReturn(detachedSponsor);
        given(store.getAccount(asTypedEvmAddress(detachedSponsorId), OnMissing.THROW))
                .willReturn(detachedSponsor);

        final var status = subject.grantKyc(detachedSponsorId, MISC);

        assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, status);
    }

    @Test
    void grantingKycRejectsDeletedAccount() {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0).setDeleted(true);

        given(store.getAccount(asTypedEvmAddress(SPONSOR), OnMissing.DONT_THROW))
                .willReturn(account);

        final var status = subject.grantKyc(SPONSOR, MISC);

        assertEquals(ACCOUNT_DELETED, status);
    }

    @Test
    void grantingRejectsUnknowableToken() {
        var account = new Account(0L, Id.fromGrpcAccount(TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(MISC));
        var tokenRelationship = new TokenRelationship(token, account);

        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);

        final var status = subject.grantKyc(TREASURY, MISC);

        assertEquals(TOKEN_HAS_NO_KYC_KEY, status);
    }

    @Test
    void unfreezingInvalidWithoutFreezeKey() {
        var account = new Account(0L, Id.fromGrpcAccount(TREASURY), 0);
        var token = new Token(Id.fromGrpcToken(MISC));
        var tokenRelationship = new TokenRelationship(token, account);

        given(store.getAccount(asTypedEvmAddress(TREASURY), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(TREASURY, MISC), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);

        final var status = subject.unfreeze(TREASURY, MISC);

        assertEquals(TOKEN_HAS_NO_FREEZE_KEY, status);
    }

    @Test
    void changingOwnerWildCardDoesTheExpectedWithTreasury() {
        final long startTreasuryNfts = 1;
        final long startCounterpartyNfts = 0;
        final long startTreasuryTNfts = 1;
        final long startCounterpartyTNfts = 0;

        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(PRIMARY_TREASURY), 0).setOwnedNfts(startTreasuryNfts);
        var counterpartyAccount =
                new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0).setOwnedNfts(startCounterpartyNfts);
        var token = new Token(Id.fromGrpcToken(T_NFT.tokenId()));
        var treasuryRel = new TokenRelationship(token, treasuryAccount).setBalance(startTreasuryTNfts);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setBalance(startCounterpartyTNfts);

        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);

        given(store.getToken(asTypedEvmAddress(T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(
                        asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwnerWildCard(T_NFT, PRIMARY_TREASURY, COUNTERPARTY);

        final var updatedPrimaryTreasury = treasuryAccount.setOwnedNfts(0);
        final var updatedCounterparty = counterpartyAccount.setOwnedNfts(1);
        final var updatedTreasuryRel = treasuryRel.setBalance(startTreasuryTNfts - 1);
        final var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyTNfts + 1);

        assertEquals(OK, status);
        verify(store).updateAccount(updatedPrimaryTreasury);
        verify(store).updateAccount(updatedCounterparty);
        verify(store).updateTokenRelationship(updatedTreasuryRel);
        verify(store).updateTokenRelationship(updatedCounterpartyRel);
    }

    @Test
    void changingOwnerWildCardRejectsFromFreezeAndKYC() {
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(PRIMARY_TREASURY), 0);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0);
        var token = new Token(Id.fromGrpcToken(T_NFT.tokenId()));
        var treasuryRel = new TokenRelationship(token, treasuryAccount).setFrozen(true);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount);

        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getTokenRelationship(
                        asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);

        final var status = subject.changeOwnerWildCard(T_NFT, PRIMARY_TREASURY, COUNTERPARTY);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void changingOwnerWildCardRejectsToFreezeAndKYC() {
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(PRIMARY_TREASURY), 0);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(COUNTERPARTY), 0);
        var token = new Token(Id.fromGrpcToken(T_NFT.tokenId()));
        var treasuryRel = new TokenRelationship(token, treasuryAccount);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setFrozen(true);

        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(PRIMARY_TREASURY), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(COUNTERPARTY), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getTokenRelationship(
                        asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(PRIMARY_TREASURY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(COUNTERPARTY, T_NFT.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);

        final var status = subject.changeOwnerWildCard(T_NFT, PRIMARY_TREASURY, COUNTERPARTY);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void updateExpiryInfoRejectsInvalidExpiry() {
        var token = new Token(Id.fromGrpcToken(MISC)).setExpiry(EXPIRY);

        final var op = updateWith(NO_KEYS, MISC, true, true, false).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(EXPIRY - 1))
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_EXPIRATION_TIME, outcome);
    }

    @Test
    void updateExpiryInfoCanExtendImmutableExpiry() {
        var token = new Token(Id.fromGrpcToken(MISC)).setExpiry(EXPIRY);
        final var op = updateWith(NO_KEYS, MISC, false, false, false).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(EXPIRY + 1_234))
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(OK, outcome);
    }

    @Test
    void updateExpiryInfoRejectsInvalidNewAutoRenew() {
        var token = new Token(Id.fromGrpcToken(MISC)).setExpiry(EXPIRY);
        final var op = updateWith(NO_KEYS, MISC, true, true, false, true, false);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(NEW_AUTO_RENEW_ACCOUNT), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_AUTORENEW_ACCOUNT, outcome);
    }

    @Test
    void updateExpiryInfoRejectsInvalidNewAutoRenewPeriod() {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0L);
        var token = new Token(Id.fromGrpcToken(MISC)).setExpiry(EXPIRY).setAutoRenewAccount(account);
        final var op = updateWith(NO_KEYS, MISC, true, true, false, false, false).toBuilder()
                .setAutoRenewPeriod(enduring(-1L))
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_RENEWAL_PERIOD, outcome);
    }

    @Test
    void updateExpiryInfoRejectsMissingToken() {
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());
        final var op = updateWith(ALL_KEYS, MISC, true, true, true);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_TOKEN_ID, outcome);
    }

    @Test
    void updateRejectsInvalidExpiry() throws InvalidKeyException {
        var token = new Token(Id.fromGrpcToken(MISC)).setExpiry(EXPIRY).setAdminKey(JKey.mapKey(KEY));
        final var op = updateWith(NO_KEYS, MISC, true, true, false).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(EXPIRY - 1))
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_EXPIRATION_TIME, outcome);
    }

    @Test
    void canExtendImmutableExpiry() {
        var token = new Token(Id.fromGrpcToken(MISC)).setExpiry(EXPIRY).setType(TokenType.FUNGIBLE_COMMON);
        final var op = updateWith(NO_KEYS, MISC, false, false, false).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(EXPIRY + 1_234))
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(OK, outcome);
    }

    @Test
    void cannotUpdateImmutableTokenWithNewFeeScheduleKey() throws InvalidKeyException {
        var token = new Token(Id.fromGrpcToken(MISC)).setExpiry(EXPIRY).setFeeScheduleKey(JKey.mapKey(KEY));

        final var op = updateWith(NO_KEYS, MISC, false, false, false).toBuilder()
                .setFeeScheduleKey(KEY)
                .setExpiry(Timestamp.newBuilder().setSeconds(EXPIRY + 1_234))
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_IS_IMMUTABLE, outcome);
    }

    @Test
    void cannotUpdateImmutableTokenWithNewPauseKey() throws InvalidKeyException {
        var token = new Token(Id.fromGrpcToken(MISC)).setExpiry(EXPIRY).setPauseKey(JKey.mapKey(KEY));

        final var op = updateWith(NO_KEYS, MISC, false, false, false).toBuilder()
                .setPauseKey(KEY)
                .setExpiry(Timestamp.newBuilder().setSeconds(EXPIRY + 1_234))
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_IS_IMMUTABLE, outcome);
    }

    @Test
    void ifImmutableWillStayImmutable() {
        var token = new Token(Id.fromGrpcToken(MISC)).setExpiry(EXPIRY);
        final var op = updateWith(ALL_KEYS, MISC, false, false, false).toBuilder()
                .setFeeScheduleKey(KEY)
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_FEE_SCHEDULE_KEY, outcome);
    }

    @Test
    void cannotUpdateNewPauseKeyIfTokenHasNoPauseKey() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var token = new Token(Id.fromGrpcToken(MISC))
                .setExpiry(EXPIRY)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey);
        final var op = updateWith(ALL_KEYS, MISC, false, false, false).toBuilder()
                .setPauseKey(KEY)
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_PAUSE_KEY, outcome);
    }

    @Test
    void updateRejectsInvalidNewAutoRenew() {
        var token = new Token(Id.fromGrpcToken(MISC));
        final var op = updateWith(NO_KEYS, MISC, true, true, false, true, false);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(NEW_AUTO_RENEW_ACCOUNT), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_AUTORENEW_ACCOUNT, outcome);
    }

    @Test
    void updateRejectsInvalidNewAutoRenewPeriod() throws InvalidKeyException {
        var account = new Account(0L, Id.fromGrpcAccount(SPONSOR), 0L);
        var jKey = JKey.mapKey(KEY);
        var token = new Token(Id.fromGrpcToken(MISC))
                .setExpiry(EXPIRY)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setAutoRenewAccount(account);
        final var op = updateWith(NO_KEYS, MISC, true, true, false, false, false).toBuilder()
                .setAutoRenewPeriod(enduring(-1L))
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_RENEWAL_PERIOD, outcome);
    }

    @Test
    void updateRejectsMissingToken() {
        final var op = updateWith(ALL_KEYS, MISC, true, true, true);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_TOKEN_ID, outcome);
    }

    @Test
    void updateRejectsInappropriateKycKey() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var token = new Token(Id.fromGrpcToken(MISC)).setAdminKey(jKey);
        final var op = updateWith(EnumSet.of(KeyType.KYC), MISC, false, false, false);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_KYC_KEY, outcome);
    }

    @Test
    void updateRejectsInappropriateFreezeKey() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var token = new Token(Id.fromGrpcToken(MISC)).setAdminKey(jKey);

        final var op = updateWith(EnumSet.of(KeyType.FREEZE), MISC, false, false, false);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_FREEZE_KEY, outcome);
    }

    @Test
    void updateRejectsInappropriateWipeKey() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var token = new Token(Id.fromGrpcToken(MISC)).setAdminKey(jKey);

        final var op = updateWith(EnumSet.of(KeyType.WIPE), MISC, false, false, false);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_WIPE_KEY, outcome);
    }

    @Test
    void updateRejectsInappropriateSupplyKey() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var token = new Token(Id.fromGrpcToken(MISC)).setAdminKey(jKey);

        final var op = updateWith(EnumSet.of(KeyType.SUPPLY), MISC, false, false, false);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, outcome);
    }

    @Test
    void updateRejectsZeroTokenBalanceKey() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var account = new Account(0L, Id.fromGrpcAccount(NEW_TREASURY), 0L);
        var token = new Token(Id.fromGrpcToken(NONFUNGIBLE))
                .setExpiry(EXPIRY)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setType(TokenType.NON_FUNGIBLE_UNIQUE);
        var tokenRelationshipKey = asTokenRelationshipKey(NEW_TREASURY, NONFUNGIBLE);
        var tokenRelationship =
                new TokenRelationship(token, account).changeKycState(true).setBalance(1L);
        final var op = updateWith(ALL_KEYS, NONFUNGIBLE, true, true, true).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(0))
                .build();

        given(store.getToken(asTypedEvmAddress(NONFUNGIBLE), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(NONFUNGIBLE), OnMissing.THROW)).willReturn(token);
        given(store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW)).willReturn(tokenRelationship);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES, outcome);
    }

    @Test
    void updateHappyPathIgnoresZeroExpiry() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var newJkey = JKey.mapKey(NEW_KEY);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(TREASURY), 0L);
        var newTreasuryAccount = new Account(0L, Id.fromGrpcAccount(NEW_TREASURY), 0L);
        var token = new Token(Id.fromGrpcToken(MISC))
                .setExpiry(EXPIRY)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(ALL_KEYS, MISC, true, true, true).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(0))
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(NEW_TREASURY), OnMissing.THROW))
                .willReturn(newTreasuryAccount);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setAdminKey(newJkey)
                .setSymbol(NEW_SYMBOL)
                .setName(NEW_NAME)
                .setTreasury(newTreasuryAccount)
                .setFreezeKey(newJkey)
                .setKycKey(newJkey)
                .setSupplyKey(newJkey)
                .setWipeKey(newJkey);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateRemovesAdminKeyWhenAppropos() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(TREASURY), 0L);
        var token = new Token(Id.fromGrpcToken(MISC))
                .setExpiry(EXPIRY)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(EnumSet.of(KeyType.EMPTY_ADMIN), MISC, false, false, false);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setAdminKey(null);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateHappyPathWorksForEverythingWithNewExpiry() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var newJkey = JKey.mapKey(NEW_KEY);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(TREASURY), 0L);
        var newTreasuryAccount = new Account(0L, Id.fromGrpcAccount(NEW_TREASURY), 0L);
        var token = new Token(Id.fromGrpcToken(MISC))
                .setExpiry(EXPIRY)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setFeeScheduleKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(ALL_KEYS, MISC, true, true, true).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(NEW_EXPIRY))
                .setFeeScheduleKey(NEW_KEY)
                .build();

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(NEW_TREASURY), OnMissing.THROW))
                .willReturn(newTreasuryAccount);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setAdminKey(newJkey)
                .setSymbol(NEW_SYMBOL)
                .setName(NEW_NAME)
                .setExpiry(NEW_EXPIRY)
                .setTreasury(newTreasuryAccount)
                .setFreezeKey(newJkey)
                .setKycKey(newJkey)
                .setSupplyKey(newJkey)
                .setWipeKey(newJkey)
                .setFeeScheduleKey(newJkey);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateHappyPathWorksWithNewMemo() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(TREASURY), 0L);
        var token = new Token(Id.fromGrpcToken(MISC))
                .setExpiry(EXPIRY)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setFeeScheduleKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(NO_KEYS, MISC, false, false, false, false, false, false, true);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setMemo(NEW_MEMO);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateHappyPathWorksWithNewMemoForNonfungible() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(TREASURY), 0L);
        var token = new Token(Id.fromGrpcToken(MISC))
                .setExpiry(EXPIRY)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setFeeScheduleKey(jKey)
                .setType(TokenType.NON_FUNGIBLE_UNIQUE)
                .setTreasury(treasuryAccount);
        final var op = updateWith(NO_KEYS, MISC, false, false, false, false, false, false, true);

        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setMemo(NEW_MEMO);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateHappyPathWorksWithNewAutoRenewAccount() throws InvalidKeyException {
        var jKey = JKey.mapKey(KEY);
        var newJkey = JKey.mapKey(NEW_KEY);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(TREASURY), 0L);
        var newTreasuryAccount = new Account(0L, Id.fromGrpcAccount(NEW_TREASURY), 0L);
        var newAccount = new Account(0L, Id.fromGrpcAccount(NEW_AUTO_RENEW_ACCOUNT), 0L);
        var token = new Token(Id.fromGrpcToken(MISC))
                .setExpiry(EXPIRY)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setFeeScheduleKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(ALL_KEYS, MISC, true, true, true, true, true);

        given(mirrorNodeEvmProperties.getMinAutoRenewDuration()).willReturn(1L);
        given(mirrorNodeEvmProperties.getMaxAutoRenewDuration()).willReturn(1_000_000_000L);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(MISC), OnMissing.THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(NEW_TREASURY), OnMissing.THROW))
                .willReturn(newTreasuryAccount);
        given(store.getAccount(asTypedEvmAddress(NEW_AUTO_RENEW_ACCOUNT), OnMissing.THROW))
                .willReturn(newAccount);
        given(store.getAccount(asTypedEvmAddress(NEW_AUTO_RENEW_ACCOUNT), OnMissing.DONT_THROW))
                .willReturn(newAccount);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setSymbol(NEW_SYMBOL)
                .setName(NEW_NAME)
                .setTreasury(newTreasuryAccount)
                .setAutoRenewAccount(newAccount)
                .setAutoRenewPeriod(NEW_AUTO_RENEW_PERIOD)
                .setAdminKey(newJkey)
                .setKycKey(newJkey)
                .setFreezeKey(newJkey)
                .setWipeKey(newJkey)
                .setSupplyKey(newJkey);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    private TokenUpdateTransactionBody updateWith(
            final EnumSet<KeyType> keys,
            final TokenID tokenId,
            final boolean useNewSymbol,
            final boolean useNewName,
            final boolean useNewTreasury) {
        return updateWith(keys, tokenId, useNewName, useNewSymbol, useNewTreasury, false, false);
    }

    private TokenUpdateTransactionBody updateWith(
            final EnumSet<KeyType> keys,
            final TokenID tokenId,
            final boolean useNewSymbol,
            final boolean useNewName,
            final boolean useNewTreasury,
            final boolean useNewAutoRenewAccount,
            final boolean useNewAutoRenewPeriod) {
        return updateWith(
                keys,
                tokenId,
                useNewSymbol,
                useNewName,
                useNewTreasury,
                useNewAutoRenewAccount,
                useNewAutoRenewPeriod,
                false,
                false);
    }

    private TokenUpdateTransactionBody updateWith(
            final EnumSet<KeyType> keys,
            final TokenID tokenId,
            final boolean useNewSymbol,
            final boolean useNewName,
            final boolean useNewTreasury,
            final boolean useNewAutoRenewAccount,
            final boolean useNewAutoRenewPeriod,
            final boolean setInvalidKeys,
            final boolean useNewMemo) {
        final var invalidKey = Key.getDefaultInstance();
        final var op = TokenUpdateTransactionBody.newBuilder().setToken(tokenId);
        if (useNewSymbol) {
            op.setSymbol(NEW_SYMBOL);
        }
        if (useNewName) {
            op.setName(NEW_NAME);
        }
        if (useNewMemo) {
            op.setMemo(StringValue.newBuilder().setValue(NEW_MEMO).build());
        }
        if (useNewTreasury) {
            op.setTreasury(NEW_TREASURY);
        }
        if (useNewAutoRenewAccount) {
            op.setAutoRenewAccount(NEW_AUTO_RENEW_ACCOUNT);
        }
        if (useNewAutoRenewPeriod) {
            op.setAutoRenewPeriod(enduring(NEW_AUTO_RENEW_PERIOD));
        }
        for (final var key : keys) {
            switch (key) {
                case WIPE -> op.setWipeKey(setInvalidKeys ? invalidKey : NEW_KEY);
                case FREEZE -> op.setFreezeKey(setInvalidKeys ? invalidKey : NEW_KEY);
                case SUPPLY -> op.setSupplyKey(setInvalidKeys ? invalidKey : NEW_KEY);
                case KYC -> op.setKycKey(setInvalidKeys ? invalidKey : NEW_KEY);
                case ADMIN -> op.setAdminKey(setInvalidKeys ? invalidKey : NEW_KEY);
                case EMPTY_ADMIN -> op.setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY);
            }
        }
        return op.build();
    }

    enum KeyType {
        WIPE,
        FREEZE,
        SUPPLY,
        KYC,
        ADMIN,
        EMPTY_ADMIN
    }

    private Duration enduring(final long secs) {
        return Duration.newBuilder().setSeconds(secs).build();
    }
}
