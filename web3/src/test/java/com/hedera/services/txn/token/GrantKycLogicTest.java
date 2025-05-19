// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
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
class GrantKycLogicTest {
    private final AccountID accountID = IdUtils.asAccount("1.2.4");
    private final TokenID tokenID = IdUtils.asToken("1.2.3");
    final TokenRelationshipKey tokenRelationshipKey =
            new TokenRelationshipKey(asTypedEvmAddress(tokenID), asTypedEvmAddress(accountID));
    private final Id idOfToken = new Id(1, 2, 3);
    private final Id idOfAccount = new Id(1, 2, 4);

    @Mock
    private Store store;

    @Mock
    private TokenRelationship modifiedRelationship;

    private TransactionBody tokenGrantKycTxn;
    private GrantKycLogic subject;

    @BeforeEach
    void setup() {
        subject = new GrantKycLogic();
    }

    @Test
    void followsHappyPath() {
        givenValidTxnCtx();
        // and:
        TokenRelationship accRel = mock(TokenRelationship.class);
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(accRel);
        given(accRel.changeKycState(true)).willReturn(modifiedRelationship);

        // when:
        subject.grantKyc(idOfToken, idOfAccount, store);

        // then:
        verify(accRel).changeKycState(true);
        verify(store).updateTokenRelationship(modifiedRelationship);
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.validate(tokenGrantKycTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.validate(tokenGrantKycTxn));
    }

    @Test
    void rejectsMissingAccount() {
        givenMissingAccount();

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.validate(tokenGrantKycTxn));
    }

    @Test
    void rejectChangeKycStateWithoutTokenKYCKey() {
        final TokenRelationship accountTokenRelationship = TokenRelationship.getEmptyTokenRelationship();

        // given:
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(accountTokenRelationship);

        // expect:
        assertFalse(accountTokenRelationship.getToken().hasKycKey());
        assertFailsWith(() -> subject.grantKyc(idOfToken, idOfAccount, store), TOKEN_HAS_NO_KYC_KEY);

        // verify:
        verify(store, never()).updateTokenRelationship(accountTokenRelationship);
    }

    private void givenValidTxnCtx() {
        tokenGrantKycTxn = TransactionBody.newBuilder()
                .setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder()
                        .setAccount(accountID)
                        .setToken(tokenID))
                .build();
    }

    private void givenMissingToken() {
        tokenGrantKycTxn = TransactionBody.newBuilder()
                .setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder())
                .build();
    }

    private void givenMissingAccount() {
        tokenGrantKycTxn = TransactionBody.newBuilder()
                .setTokenGrantKyc(TokenGrantKycTransactionBody.newBuilder().setToken(tokenID))
                .build();
    }
}
