// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokeKycLogicTest {

    private final AccountID accountID = IdUtils.asAccount("1.2.4");
    private final TokenID tokenID = IdUtils.asToken("1.2.3");
    private final TokenRelationshipKey tokenRelationshipKey =
            new TokenRelationshipKey(asTypedEvmAddress(tokenID), asTypedEvmAddress(accountID));
    private final Id idOfToken = new Id(1, 2, 3);
    private final Id idOfAccount = new Id(1, 2, 4);

    @Mock
    private Store store;

    @Mock
    private TokenRelationship tokenRelationship;

    private RevokeKycLogic subject;
    private TransactionBody tokenRevokeKycBody;

    @BeforeEach
    void setUp() {
        subject = new RevokeKycLogic();
    }

    @Test
    void followsHappyPath() {
        givenValidTxnCtx();
        // and:
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(tokenRelationship);

        TokenRelationship tokenRelationshipResult = mock(TokenRelationship.class);
        given(tokenRelationship.changeKycState(false)).willReturn(tokenRelationshipResult);

        // when:
        subject.revokeKyc(idOfToken, idOfAccount, store);

        // then:
        verify(tokenRelationship).changeKycState(false);
        verify(store).updateTokenRelationship(tokenRelationshipResult);
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.validate(tokenRevokeKycBody));
    }

    @Test
    void rejectMissingToken() {
        givenMissingTokenTxnCtx();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.validate(tokenRevokeKycBody));
    }

    @Test
    void rejectMissingAccount() {
        givenMissingAccountTxnCtx();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.validate(tokenRevokeKycBody));
    }

    @Test
    void rejectChangeKycStateWithoutTokenKYCKey() {
        final TokenRelationship emptyTokenRelationship = TokenRelationship.getEmptyTokenRelationship();

        // given:
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(emptyTokenRelationship);

        // expect:
        Assertions.assertFalse(emptyTokenRelationship.getToken().hasKycKey());
        assertFailsWith(() -> subject.revokeKyc(idOfToken, idOfAccount, store), TOKEN_HAS_NO_KYC_KEY);

        // verify:
        verify(store, never()).updateTokenRelationship(emptyTokenRelationship);
    }

    private void givenValidTxnCtx() {
        buildTxnContext(accountID, tokenID);
    }

    private void givenMissingTokenTxnCtx() {
        tokenRevokeKycBody = TransactionBody.newBuilder()
                .setTokenRevokeKyc(TokenRevokeKycTransactionBody.newBuilder().setAccount(accountID))
                .build();
    }

    private void givenMissingAccountTxnCtx() {
        tokenRevokeKycBody = TransactionBody.newBuilder()
                .setTokenRevokeKyc(TokenRevokeKycTransactionBody.newBuilder().setToken(tokenID))
                .build();
    }

    private void buildTxnContext(AccountID accountID, TokenID tokenID) {
        tokenRevokeKycBody = TransactionBody.newBuilder()
                .setTokenRevokeKyc(TokenRevokeKycTransactionBody.newBuilder()
                        .setAccount(accountID)
                        .setToken(tokenID))
                .build();
    }
}
