// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BurnLogicTest {
    private final long amount = 123L;
    private final Id id = new Id(1, 2, 3);
    private final TokenID grpcId = id.asGrpcToken();
    private final Id treasuryId = new Id(2, 4, 6);
    private final Account treasury = new Account(0L, treasuryId, 0);
    private TokenRelationship treasuryRel;

    @Mock
    private Token token;

    @Mock
    private OptionValidator validator;

    @Mock
    private Store store;

    @Mock
    private TokenModificationResult tokenModificationResult;

    @Mock
    private Token updatedToken;

    @Mock
    private TokenRelationship modifiedTreasuryRel;

    @Mock
    private Token tokenAfterBurn;

    @Mock
    private TokenRelationship treasuryRelAfterBurn;

    private TransactionBody tokenBurnTxn;

    private BurnLogic subject;

    @BeforeEach
    void setup() {
        subject = new BurnLogic(validator);
    }

    @Test
    void followsHappyPathForCommon() {
        // setup:
        treasuryRel = new TokenRelationship(token, treasury);

        givenValidTxnCtx();
        given(store.getToken(id.asEvmAddress(), OnMissing.THROW)).willReturn(token);
        given(token.getId()).willReturn(id);
        given(token.getTreasury()).willReturn(treasury);
        given(store.getTokenRelationship(
                        new TokenRelationshipKey(token.getId().asEvmAddress(), treasury.getAccountAddress()),
                        OnMissing.THROW))
                .willReturn(treasuryRel);
        given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
        given(token.burn(treasuryRel, amount)).willReturn(tokenModificationResult);
        given(tokenModificationResult.token()).willReturn(updatedToken);
        given(tokenModificationResult.tokenRelationship()).willReturn(modifiedTreasuryRel);
        // when:
        subject.burn(id, amount, Collections.emptyList(), store);

        // then:
        verify(token).burn(treasuryRel, amount);
        verify(store).updateToken(updatedToken);
        verify(store).updateTokenRelationship(modifiedTreasuryRel);
    }

    @Test
    void followsHappyPathForUnique() {
        // setup:
        final var serials = List.of(1L, 2L);
        treasuryRel = new TokenRelationship(token, treasury);

        givenValidUniqueTxnCtx();
        given(token.getTreasury()).willReturn(treasury);
        given(store.getToken(id.asEvmAddress(), OnMissing.THROW)).willReturn(token);
        given(token.getId()).willReturn(id);
        given(token.getTreasury()).willReturn(treasury);
        given(store.getTokenRelationship(
                        new TokenRelationshipKey(token.getId().asEvmAddress(), treasury.getAccountAddress()),
                        OnMissing.THROW))
                .willReturn(treasuryRel);
        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        given(store.loadUniqueTokens(token, serials)).willReturn(updatedToken);
        given(tokenAfterBurn.getTreasury()).willReturn(treasury);
        given(updatedToken.burn(treasuryRel, serials)).willReturn(tokenModificationResult);
        given(tokenModificationResult.token()).willReturn(tokenAfterBurn);
        given(tokenModificationResult.tokenRelationship()).willReturn(treasuryRelAfterBurn);

        // when:
        subject.burn(id, amount, serials, store);

        // then:
        verify(token).getType();
        verify(updatedToken).burn(treasuryRel, serials);
        verify(store).updateToken(tokenAfterBurn);
        verify(store).updateTokenRelationship(treasuryRelAfterBurn);
        verify(store).updateAccount(any(Account.class));
    }

    @Test
    void precheckWorksForZeroFungibleAmount() {
        givenValidTxnCtxWithZeroAmount();
        assertEquals(OK, subject.validateSyntax(tokenBurnTxn));
    }

    @Test
    void precheckWorksForNonZeroFungibleAmount() {
        givenUniqueTxnCtxWithNoSerials();
        assertEquals(OK, subject.validateSyntax(tokenBurnTxn));
    }

    private void givenValidTxnCtx() {
        tokenBurnTxn = TransactionBody.newBuilder()
                .setTokenBurn(
                        TokenBurnTransactionBody.newBuilder().setToken(grpcId).setAmount(amount))
                .build();
    }

    private void givenValidTxnCtxWithZeroAmount() {
        tokenBurnTxn = TransactionBody.newBuilder()
                .setTokenBurn(
                        TokenBurnTransactionBody.newBuilder().setToken(grpcId).setAmount(0))
                .build();
    }

    private void givenUniqueTxnCtxWithNoSerials() {
        tokenBurnTxn = TransactionBody.newBuilder()
                .setTokenBurn(
                        TokenBurnTransactionBody.newBuilder().setToken(grpcId).addAllSerialNumbers(List.of()))
                .build();
    }

    private void givenValidUniqueTxnCtx() {
        tokenBurnTxn = TransactionBody.newBuilder()
                .setTokenBurn(
                        TokenBurnTransactionBody.newBuilder().setToken(grpcId).addAllSerialNumbers(List.of(1L)))
                .build();
    }
}
