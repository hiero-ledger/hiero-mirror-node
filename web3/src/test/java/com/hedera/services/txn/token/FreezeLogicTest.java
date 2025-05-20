// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreezeLogicTest {

    private final Id idOfToken = new Id(1, 2, 3);
    private final Id idOfAccount = new Id(1, 2, 4);
    private final AccountID accountID = IdUtils.asAccount("1.2.4");
    private final TokenID tokenID = IdUtils.asToken("1.2.3");
    final TokenRelationshipKey tokenRelationshipKey =
            new TokenRelationshipKey(asTypedEvmAddress(tokenID), asTypedEvmAddress(accountID));

    @Mock
    private Store store;

    private FreezeLogic subject;
    private TransactionBody tokenFreezeTxn;

    @BeforeEach
    void setup() {
        subject = new FreezeLogic();
    }

    @Test
    void followsHappyPath() {
        // given:
        TokenRelationship tokenRelationship = mock(TokenRelationship.class);
        TokenRelationship modifiedTokenRelationship = mock(TokenRelationship.class);
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(tokenRelationship);
        given(tokenRelationship.changeFrozenState(true)).willReturn(modifiedTokenRelationship);

        // when:
        subject.freeze(idOfToken, idOfAccount, store);

        // then:
        verify(tokenRelationship).changeFrozenState(true);
        verify(store).updateTokenRelationship(modifiedTokenRelationship);
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.validate(tokenFreezeTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.validate(tokenFreezeTxn));
    }

    @Test
    void rejectsMissingAccount() {
        givenMissingAccount();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.validate(tokenFreezeTxn));
    }

    @Test
    void rejectChangeFrozenStateWithoutTokenFreezeKey() {
        final TokenRelationship tokenRelationship = TokenRelationship.getEmptyTokenRelationship();

        // given:
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(tokenRelationship);

        // expect:
        assertFalse(tokenRelationship.getToken().hasFreezeKey());
        assertFailsWith(() -> subject.freeze(idOfToken, idOfAccount, store), TOKEN_HAS_NO_FREEZE_KEY);

        // verify:
        verify(store, never()).updateTokenRelationship(tokenRelationship);
    }

    private void givenValidTxnCtx() {
        tokenFreezeTxn = TransactionBody.newBuilder()
                .setTokenFreeze(TokenFreezeAccountTransactionBody.newBuilder()
                        .setAccount(accountID)
                        .setToken(tokenID))
                .build();
    }

    private void givenMissingToken() {
        tokenFreezeTxn = TransactionBody.newBuilder()
                .setTokenFreeze(TokenFreezeAccountTransactionBody.newBuilder())
                .build();
    }

    private void givenMissingAccount() {
        tokenFreezeTxn = TransactionBody.newBuilder()
                .setTokenFreeze(TokenFreezeAccountTransactionBody.newBuilder().setToken(tokenID))
                .build();
    }
}
