// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.RoyaltyFee;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateLogicTest {

    @Mock
    private TokenCreateTransactionBody op;

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @Mock
    private Store store;

    @Mock
    private Account account;

    @Mock
    private Account treasury;

    @Mock
    private OptionValidator optionValidator;

    @Mock
    private Id accountId;

    @Mock
    private Id treasuryId;

    @Mock
    private Id tokenId;

    @Mock
    private Id denomTokenId;

    @Mock
    private Token token;

    @Mock
    private Token denomToken;

    private final Address address = Address.fromHexString("0x000000000000000000000000000000000000077e");
    private final Address treasuryAddress = Address.fromHexString("0x000000000000000000000000000000000000078e");
    private final Address tokenAddress = Address.fromHexString("0x0000000000000000000000000000000000000182");
    private CreateLogic createLogic;

    private MockedStatic<Id> staticMock;
    private MockedStatic<Token> staticToken;

    @BeforeEach
    void setup() {
        createLogic = new CreateLogic(evmProperties, new AssociateLogic(evmProperties));
        staticMock = Mockito.mockStatic(Id.class);
        staticToken = Mockito.mockStatic(Token.class);
    }

    @AfterEach
    void clean() {
        staticMock.close();
        staticToken.close();
    }

    @Test
    void throwExceptionIfTokenIsExpired() {
        given(op.hasExpiry()).willReturn(true);
        given(op.getExpiry()).willReturn(Timestamp.getDefaultInstance());
        final var treasuryAccount = AccountID.getDefaultInstance();
        given(op.getTreasury()).willReturn(treasuryAccount);
        staticMock.when(() -> Id.fromGrpcAccount(treasuryAccount)).thenReturn(accountId);
        assertThatThrownBy(this::runCreateLogic).isInstanceOf(InvalidTransactionException.class);
    }

    private void runCreateLogic() {
        createLogic.create(Instant.now().getEpochSecond(), address, optionValidator, store, op);
    }

    @Test
    void throwIfTooManyFees() {
        given(op.hasExpiry()).willReturn(false);
        final var treasuryAccount = AccountID.getDefaultInstance();
        given(op.getTreasury()).willReturn(treasuryAccount);
        staticMock.when(() -> Id.fromGrpcAccount(treasuryAccount)).thenReturn(accountId);
        given(op.hasAutoRenewAccount()).willReturn(false);
        given(op.getCustomFeesCount()).willReturn(11);
        assertThatThrownBy(this::runCreateLogic).isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void verifyPersist() {
        given(op.hasExpiry()).willReturn(false);
        staticMock.when(() -> Id.fromGrpcAccount(any())).thenReturn(treasuryId).thenReturn(accountId);
        staticMock.when(() -> Id.fromGrpcToken(any())).thenReturn(tokenId);
        given(tokenId.asEvmAddress()).willReturn(tokenAddress);
        given(store.getAccount(any(), any())).willReturn(treasury).willReturn(account);
        given(op.hasAutoRenewAccount()).willReturn(true);
        staticToken.when(Token::getEmptyToken).thenCallRealMethod();
        staticToken
                .when(() -> Token.fromGrpcOpAndMeta(
                        any(Id.class),
                        any(TokenCreateTransactionBody.class),
                        any(Account.class),
                        any(Account.class),
                        any(long.class)))
                .thenReturn(token);
        given(token.getId()).willReturn(tokenId);
        given(token.newEnabledRelationship(any()))
                .willReturn(new TokenRelationship(token, account, () -> 0L, false, true, false, false, false, 0));
        given(token.getTreasury()).willReturn(treasury);
        given(treasury.getId()).willReturn(treasuryId);
        given(token.getCustomFees()).willReturn(Collections.emptyList());
        given(evmProperties.getMaxTokensPerAccount()).willReturn(1000);
        given(op.getInitialSupply()).willReturn(10L);

        runCreateLogic();
        verify(store, atLeast(2)).updateAccount(any());
        verify(store).updateToken(any());
        verify(store).updateTokenRelationship(any());
    }

    @Test
    void verifyPersistWithFees() {
        given(op.hasExpiry()).willReturn(false);
        staticMock.when(() -> Id.fromGrpcAccount(any())).thenReturn(treasuryId).thenReturn(accountId);
        staticMock.when(() -> Id.fromGrpcToken(any())).thenReturn(tokenId);
        given(tokenId.asEvmAddress()).willReturn(tokenAddress);
        given(store.getAccount(any(), any())).willReturn(treasury).willReturn(account);
        given(op.hasAutoRenewAccount()).willReturn(true);
        staticToken.when(Token::getEmptyToken).thenCallRealMethod();
        staticToken
                .when(() -> Token.fromGrpcOpAndMeta(
                        any(Id.class),
                        any(TokenCreateTransactionBody.class),
                        any(Account.class),
                        any(Account.class),
                        any(long.class)))
                .thenReturn(token);
        given(token.newEnabledRelationship(any()))
                .willReturn(new TokenRelationship(token, account, () -> 0L, false, true, false, false, false, 0));
        given(token.getId()).willReturn(tokenId);
        given(token.getTreasury()).willReturn(treasury);
        given(treasury.getId()).willReturn(treasuryId);
        given(token.getCustomFees()).willReturn(prepareFees());
        given(token.isNonFungibleUnique()).willReturn(true);
        given(token.isFungibleCommon()).willReturn(true);
        given(evmProperties.getMaxTokensPerAccount()).willReturn(1000);
        given(op.getInitialSupply()).willReturn(10L);
        given(store.hasAssociation(any())).willReturn(true).willReturn(true).willReturn(false);
        prepareDenomToken();
        runCreateLogic();
        verify(store, atLeast(2)).updateAccount(any());
        verify(store).updateToken(any());
        verify(store, atLeast(2)).updateTokenRelationship(any());
    }

    @Test
    void verifySetNewDenominationToken() {
        given(op.hasExpiry()).willReturn(false);
        staticMock.when(() -> Id.fromGrpcAccount(any())).thenReturn(treasuryId).thenReturn(accountId);
        staticMock.when(() -> Id.fromGrpcToken(any())).thenReturn(tokenId);
        given(tokenId.asEvmAddress()).willReturn(tokenAddress);
        given(token.newEnabledRelationship(any()))
                .willReturn(new TokenRelationship(token, account, () -> 0L, false, true, false, false, false, 0));
        given(store.getAccount(any(), any())).willReturn(treasury).willReturn(account);
        given(op.hasAutoRenewAccount()).willReturn(true);
        staticToken.when(Token::getEmptyToken).thenCallRealMethod();
        staticToken
                .when(() -> Token.fromGrpcOpAndMeta(
                        any(Id.class),
                        any(TokenCreateTransactionBody.class),
                        any(Account.class),
                        any(Account.class),
                        any(long.class)))
                .thenReturn(token);
        given(token.getId()).willReturn(tokenId);
        given(token.getTreasury()).willReturn(treasury);
        given(treasury.getId()).willReturn(treasuryId);
        var customFee = prepareFixedFee();
        var feeSpy = spy(customFee);
        given(token.getCustomFees()).willReturn(List.of(feeSpy));
        given(token.isFungibleCommon()).willReturn(true);
        given(evmProperties.getMaxTokensPerAccount()).willReturn(1000);
        given(op.getInitialSupply()).willReturn(10L);
        runCreateLogic();
        verify(feeSpy).setFixedFee(any());
    }

    private List<CustomFee> prepareFees() {
        final var fixedFee = new FixedFee(3, null, true, true, treasuryAddress);
        final var royaltyFee = new RoyaltyFee(1, 1, 3, null, true, treasuryAddress);
        final var fractionalFee = new FractionalFee(1, 1, 4, 5, true, treasuryAddress);
        final var customFixedFee = new CustomFee();
        customFixedFee.setFixedFee(fixedFee);
        final var customRoyaltyFee = new CustomFee();
        customRoyaltyFee.setRoyaltyFee(royaltyFee);
        final var customFractionalFee = new CustomFee();
        customFractionalFee.setFractionalFee(fractionalFee);
        return List.of(customFixedFee, customRoyaltyFee, customFractionalFee);
    }

    private CustomFee prepareFixedFee() {
        final var fixedFee = new FixedFee(3, null, false, true, treasuryAddress);
        final var customFee = new CustomFee();
        customFee.setFixedFee(fixedFee);
        return customFee;
    }

    private void prepareDenomToken() {
        given(store.getToken(any(), any())).willReturn(denomToken);
        given(denomToken.isFungibleCommon()).willReturn(true);
        given(denomToken.getId()).willReturn(denomTokenId);
    }
}
