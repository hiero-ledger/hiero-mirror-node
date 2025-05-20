// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WipeLogicTest {
    private final AccountID accountID = IdUtils.asAccount("1.2.4");
    private final TokenID id = IdUtils.asToken("1.2.3");
    final TokenRelationshipKey tokenRelationshipKey =
            new TokenRelationshipKey(asTypedEvmAddress(id), asTypedEvmAddress(accountID));
    private final Id idOfToken = new Id(1, 2, 3);
    private final Id idOfAccount = new Id(1, 2, 4);
    private final long wipeAmount = 100;

    private TransactionBody tokenWipeTxn;
    private TokenRelationship treasuryRel;

    @Mock
    private Token token;

    @Mock
    private Store store;

    @Mock
    private TokenModificationResult tokenModificationResult;

    @Mock
    private Token updatedToken;

    @Mock
    private TokenRelationship modifiedTreasuryRel;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    private WipeLogic subject;

    @BeforeEach
    void setup() {
        subject = new WipeLogic(mirrorNodeEvmProperties);
    }

    @Test
    void followsHappyPathForCommon() {
        givenValidCommonTxnCtx();

        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(treasuryRel);
        given(token.wipe(treasuryRel, wipeAmount)).willReturn(tokenModificationResult);
        given(tokenModificationResult.token()).willReturn(updatedToken);
        given(tokenModificationResult.tokenRelationship()).willReturn(modifiedTreasuryRel);

        // when:
        subject.wipe(idOfToken, idOfAccount, wipeAmount, Collections.emptyList(), store);

        // then:
        verify(token).wipe(any(), anyLong());
        verify(store).updateToken(updatedToken);
    }

    @Test
    void followsHappyPathForUnique() {
        Account account = new Account(0L, idOfAccount, 0);
        treasuryRel = new TokenRelationship(token, account);

        givenValidUniqueTxnCtx();
        final var serials = List.of(1L, 2L, 3L);
        // needed only in the context of this test
        TokenRelationship accRel = mock(TokenRelationship.class);
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(accRel);
        given(store.getTokenRelationship(tokenRelationshipKey, Store.OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.loadUniqueTokens(token, serials)).willReturn(updatedToken);
        given(updatedToken.wipe(treasuryRel, serials)).willReturn(tokenModificationResult);
        given(tokenModificationResult.token()).willReturn(updatedToken);
        given(tokenModificationResult.tokenRelationship()).willReturn(modifiedTreasuryRel);
        given(modifiedTreasuryRel.getAccount()).willReturn(account);

        // when:
        subject.wipe(idOfToken, idOfAccount, wipeAmount, serials, store);

        // then:
        verify(updatedToken).wipe(any(TokenRelationship.class), anyList());
        verify(token).getType();
        verify(store).updateToken(updatedToken);
        verify(store).updateAccount(any(Account.class));
    }

    @Test
    void validatesSyntax() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(id)
                        .setAccount(accountID)
                        .addAllSerialNumbers(List.of(1L, 2L, 3L)))
                .build();

        given(mirrorNodeEvmProperties.getMaxBatchSizeWipe()).willReturn(10);

        assertEquals(OK, subject.validateSyntax(tokenWipeTxn));
    }

    @Test
    void validatesSyntaxError() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(id)
                        .setAccount(accountID)
                        .addAllSerialNumbers(List.of(1L, 2L, 3L)))
                .build();

        given(mirrorNodeEvmProperties.getMaxBatchSizeWipe()).willReturn(1);

        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.validateSyntax(tokenWipeTxn));
    }

    private void givenValidCommonTxnCtx() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(id)
                        .setAccount(accountID)
                        .setAmount(wipeAmount))
                .build();
        given(store.getToken(any(), any())).willReturn(token);
        given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
    }

    private void givenValidUniqueTxnCtx() {
        tokenWipeTxn = TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setToken(id)
                        .setAccount(accountID)
                        .addAllSerialNumbers(List.of(1L, 2L, 3L)))
                .build();
        given(store.getToken(any(), any())).willReturn(token);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
    }
}
