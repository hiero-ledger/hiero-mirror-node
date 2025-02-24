// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssociateLogicTest {

    private final Address accountAddress = Address.fromHexString("0x000000000000000000000000000000000000077e");
    private final Address tokenAddress = Address.fromHexString("0x0000000000000000000000000000000000000182");
    private final List<Address> tokenAddresses = List.of(tokenAddress);

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Mock
    private StoreImpl store;

    @Mock
    private Account account;

    @Mock
    private Token token;

    private AssociateLogic associateLogic;

    @BeforeEach
    public void setUp() {
        associateLogic = new AssociateLogic(mirrorNodeEvmProperties);
    }

    @Test
    void throwErrorWhenAccountNotFound() {
        when(store.getAccount(accountAddress, OnMissing.THROW))
                .thenThrow(getException(Account.class.getName(), accountAddress));
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses, store))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue(
                        "message",
                        String.format(
                                "Entity of type %s with id %s is missing", Account.class.getName(), accountAddress));
    }

    @Test
    void throwErrorWhenTokenNotFound() {
        when(store.getAccount(accountAddress, OnMissing.THROW)).thenReturn(account);
        when(store.getToken(tokenAddress, OnMissing.THROW))
                .thenThrow(getException(Token.class.getName(), tokenAddress));
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses, store))
                .isInstanceOf(InvalidTransactionException.class)
                .hasFieldOrPropertyWithValue(
                        "message",
                        String.format("Entity of type %s with id %s is missing", Token.class.getName(), tokenAddress));
    }

    @Test
    void failsOnCrossingAssociationLimit() {
        when(store.getAccount(accountAddress, OnMissing.THROW)).thenReturn(account);
        when(account.getNumAssociations()).thenReturn(5);
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(3);

        // expect:
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses, store))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED.name());
    }

    @Test
    void failsOnAssociatingWithAlreadyRelatedToken() {
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(1000);
        setupAccount(1);
        setupToken();

        final var tokenRelationShipKey = new TokenRelationshipKey(tokenAddress, accountAddress);
        when(store.hasAssociation(tokenRelationShipKey)).thenReturn(true);
        assertThatThrownBy(() -> associateLogic.associate(accountAddress, tokenAddresses, store))
                .isInstanceOf(com.hedera.node.app.service.evm.exceptions.InvalidTransactionException.class)
                .hasMessage(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT.name());
    }

    @Test
    void canAssociateWithNewToken() {
        final var modifiedAccount = setupAccount(1);
        final var newRel =
                new TokenRelationship(token, modifiedAccount, () -> 0L, false, false, false, false, false, 0);
        setupToken();
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(1000);
        when(token.newRelationshipWith(modifiedAccount, false)).thenReturn(newRel);

        associateLogic.associate(accountAddress, tokenAddresses, store);

        verify(store).updateTokenRelationship(newRel);
    }

    @Test
    void updatesAccount() {
        when(mirrorNodeEvmProperties.getMaxTokensPerAccount()).thenReturn(1000);
        final var modifiedAccount = setupAccount(6);
        when(account.getNumAssociations()).thenReturn(5);

        setupToken();

        associateLogic.associate(accountAddress, tokenAddresses, store);

        verify(store).updateAccount(modifiedAccount);
        verify(account).setNumAssociations(6);
    }

    private Account setupAccount(int numOfAssociations) {
        when(store.getAccount(accountAddress, OnMissing.THROW)).thenReturn(account);
        when(account.getAccountAddress()).thenReturn(accountAddress);
        Account modifiedAccount = mock(Account.class);
        when(account.setNumAssociations(numOfAssociations)).thenReturn(modifiedAccount);
        return modifiedAccount;
    }

    private void setupToken() {
        when(store.getToken(tokenAddress, OnMissing.THROW)).thenReturn(token);
        Id tokenId = mock(Id.class);
        when(token.getId()).thenReturn(tokenId);
        when(tokenId.asEvmAddress()).thenReturn(tokenAddress);
    }

    private InvalidTransactionException getException(final String type, Object id) {
        return new InvalidTransactionException(
                String.format("Entity of type %s with id %s is missing", type, id), FAIL_INVALID, true);
    }
}
