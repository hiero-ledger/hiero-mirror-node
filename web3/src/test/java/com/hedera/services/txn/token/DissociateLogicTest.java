// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import java.util.List;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DissociateLogicTest {

    private final Address accountAddress = Address.fromHexString("0x000000000000000000000000000000000000077e");
    private final Address tokenAddress = Address.fromHexString("0x0000000000000000000000000000000000000182");
    private final List<Address> tokenAddresses = List.of(tokenAddress);

    @Mock
    private Store store;

    private Account account;

    @Mock
    private Id accountId;

    @Mock
    private Id treasuryId;

    @Mock
    private Id tokenId;

    @Mock
    private Token token;

    private TokenRelationship tokenRelationship;

    private DissociateLogic dissociateLogic;

    private Account spyAccount;

    @BeforeEach
    public void beforeEach() {
        when(accountId.asEvmAddress()).thenReturn(accountAddress);
        account = new Account(0L, accountId, 0L);
        tokenRelationship = new TokenRelationship(token, account);
        dissociateLogic = new DissociateLogic();
        spyAccount = spy(account);
    }

    @Test
    void throwExceptionIfAccountNotFound() {
        when(store.getAccount(accountAddress, OnMissing.THROW))
                .thenThrow(getException(Account.class.getName(), accountAddress));
        assertThatThrownBy(() -> dissociateLogic.dissociate(accountAddress, tokenAddresses, store))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue(
                        "message",
                        String.format(
                                "Entity of type %s with id %s is missing", Account.class.getName(), accountAddress));
    }

    @Test
    void throwExceptionIfTokenNotFound() {
        when(store.getAccount(accountAddress, OnMissing.THROW)).thenReturn(account);
        when(store.getToken(tokenAddress, OnMissing.THROW))
                .thenThrow(getException(Token.class.getName(), tokenAddress));
        assertThatThrownBy(() -> dissociateLogic.dissociate(accountAddress, tokenAddresses, store))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue(
                        "message",
                        String.format("Entity of type %s with id %s is missing", Token.class.getName(), tokenAddress));
    }

    @Test
    void throwExceptionIfTokenIsNotAssocWithAccount() {
        setupAccount();
        setupToken();
        assertThatThrownBy(() -> dissociateLogic.dissociate(accountAddress, tokenAddresses, store))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.name());
    }

    @Test
    void verifyTokenDissociated() {
        tokenRelationship = tokenRelationship.setNotYetPersisted(false);
        final var updatedSpy = spyAccount.setNumAssociations(3);
        spyAccount = spy(updatedSpy);
        setupAccount();
        setupToken();
        setupTokenRelationship();
        when(token.getTreasury()).thenReturn(mock(Account.class));
        dissociateLogic.dissociate(accountAddress, tokenAddresses, store);

        verify(spyAccount).setNumAssociations(anyInt());
        var expectedAccount = new Account(0L, accountId, 0L);
        expectedAccount = expectedAccount.setNumAssociations(2);
        verify(store).updateAccount(expectedAccount);
    }

    @Test
    void verifyDissociationForDeletedToken() {
        final var initialOwnedNfts = 5L;
        final var balanceToDeduct = 3L;
        final var updatedSpy = spyAccount.setOwnedNfts(initialOwnedNfts);
        spyAccount = spy(updatedSpy);
        tokenRelationship = tokenRelationship.setBalance(balanceToDeduct);
        tokenRelationship = tokenRelationship.setNotYetPersisted(false);
        tokenRelationship = tokenRelationship.setAccount(spyAccount);

        setupToken();

        final var expectedTokenRelationship = tokenRelationship
                .markAsDestroyed()
                .setBalance(0L)
                .setAccount(spyAccount.setOwnedNfts(initialOwnedNfts - balanceToDeduct));

        when(store.hasAssociation(any(TokenRelationshipKey.class))).thenReturn(true);
        when(store.getTokenRelationship(any(TokenRelationshipKey.class), any(OnMissing.class)))
                .thenReturn(tokenRelationship)
                .thenReturn(expectedTokenRelationship);

        when(token.getType()).thenReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        setupAccount();
        when(token.isDeleted()).thenReturn(true);

        dissociateLogic.dissociate(accountAddress, tokenAddresses, store);

        verify(store).deleteTokenRelationship(expectedTokenRelationship);
    }

    @Test
    void throwExceptionIfTokenNotExpired() {
        tokenRelationship = tokenRelationship.setBalance(3L);
        tokenRelationship = tokenRelationship.setNotYetPersisted(false);
        setupToken();
        setupTokenRelationship();
        setupAccount();
        when(token.getTreasury()).thenReturn(mock(Account.class));
        when(token.isDeleted()).thenReturn(false);
        when(token.getExpiry()).thenReturn(9999999999L);

        assertThatThrownBy(() -> dissociateLogic.dissociate(accountAddress, tokenAddresses, store))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES.name());
    }

    @Test
    void throwExceptionIfTokenTypeNFT() {
        tokenRelationship = tokenRelationship.setBalance(3L);
        tokenRelationship = tokenRelationship.setNotYetPersisted(false);
        setupToken();
        setupTokenRelationship();
        setupAccount();
        when(token.getTreasury()).thenReturn(mock(Account.class));
        when(token.isDeleted()).thenReturn(false);
        when(token.getType()).thenReturn(TokenType.NON_FUNGIBLE_UNIQUE);

        assertThatThrownBy(() -> dissociateLogic.dissociate(accountAddress, tokenAddresses, store))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(ACCOUNT_STILL_OWNS_NFTS.name());
    }

    @Test
    void verifyActiveTokenBalanceSentToTreasury() {
        tokenRelationship = tokenRelationship.setBalance(3L);
        tokenRelationship = tokenRelationship.setNotYetPersisted(false);
        setupToken();
        setupTokenRelationship();
        setupAccount();
        final var treasury = new Account(0L, treasuryId, 15L);
        final var spiedTreasury = spy(treasury);
        when(token.getTreasury()).thenReturn(spiedTreasury);
        when(token.isDeleted()).thenReturn(false);

        dissociateLogic.dissociate(accountAddress, tokenAddresses, store);
        verify(spiedTreasury).setBalance(18L);
        var updatedTreasury = new Account(0L, treasuryId, 18L);
        verify(store).updateAccount(updatedTreasury);
    }

    @Test
    void verifyDecrementedAutoAssociations() {
        var newAccount = new Account(
                ByteString.EMPTY,
                0L,
                accountId,
                9999999999L,
                () -> 0L,
                false,
                () -> 0L,
                0L,
                null,
                3,
                null,
                null,
                null,
                () -> 3,
                () -> 0,
                0,
                0,
                false,
                null,
                0L,
                0);
        newAccount = newAccount.setUsedAutoAssociations(3);
        spyAccount = spy(newAccount);
        tokenRelationship =
                new TokenRelationship(token, spyAccount, () -> 3L, false, !token.hasKycKey(), false, false, true, 3L);
        setupToken();
        setupTokenRelationship();
        setupAccount();
        when(token.getTreasury()).thenReturn(mock(Account.class));
        when(token.isDeleted()).thenReturn(false);

        dissociateLogic.dissociate(accountAddress, tokenAddresses, store);
        verify(spyAccount).decrementUsedAutomaticAssociations();
    }

    @Test
    void verifyUpdagtedNumPositiveBalance() {
        var newAccount = new Account(
                ByteString.EMPTY,
                0L,
                accountId,
                9999999999L,
                () -> 0L,
                false,
                () -> 0L,
                0L,
                null,
                3,
                null,
                null,
                null,
                () -> 3,
                () -> 3,
                0,
                0,
                false,
                null,
                0L,
                0);
        newAccount = newAccount.setUsedAutoAssociations(3);
        spyAccount = spy(newAccount);
        tokenRelationship =
                new TokenRelationship(token, spyAccount, () -> 3L, false, !token.hasKycKey(), false, false, false, 0L);
        setupToken();
        setupTokenRelationship();
        setupAccount();
        when(token.getTreasury()).thenReturn(mock(Account.class));
        when(token.isDeleted()).thenReturn(false);

        dissociateLogic.dissociate(accountAddress, tokenAddresses, store);
        verify(spyAccount).setNumPositiveBalances(any(Integer.class));
    }

    private InvalidTransactionException getException(final String type, Object id) {
        return new InvalidTransactionException(
                String.format("Entity of type %s with id %s is missing", type, id), FAIL_INVALID, true);
    }

    private void setupAccount() {
        when(store.getAccount(any(Address.class), any(OnMissing.class))).thenReturn(spyAccount);
    }

    private void setupToken() {
        when(store.getToken(tokenAddress, OnMissing.THROW)).thenReturn(token);
        when(token.getId()).thenReturn(tokenId);
        when(tokenId.asEvmAddress()).thenReturn(tokenAddress);
    }

    private void setupTokenRelationship() {
        when(store.hasAssociation(any(TokenRelationshipKey.class))).thenReturn(true);
        when(store.getTokenRelationship(any(TokenRelationshipKey.class), any(OnMissing.class)))
                .thenReturn(tokenRelationship);
    }
}
