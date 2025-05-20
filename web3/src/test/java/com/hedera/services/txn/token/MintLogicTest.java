// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenModificationResult;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MintLogicTest {

    private final long amount = 123L;
    private final Id id = new Id(1, 2, 3);
    private final TokenID grpcId = id.asGrpcToken();
    private final Id treasuryId = new Id(2, 4, 6);
    private final Account treasury = new Account(0L, treasuryId, 0L);

    @Mock
    private Token token;

    @Mock
    private Store store;

    @Mock
    private OptionValidator validator;

    @Mock
    private TokenModificationResult tokenModificationResult;

    @Mock
    private Token tokenAfterMint;

    @Mock
    private TokenRelationship treasuryRelAfterMint;

    private TokenRelationship treasuryRel;
    private TransactionBody tokenMintTxn;

    private MintLogic subject;

    @BeforeEach
    void setup() {
        subject = new MintLogic(validator);
    }

    @Test
    void followsHappyPath() {
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
        given(token.mint(treasuryRel, amount, false)).willReturn(tokenModificationResult);
        given(tokenModificationResult.token()).willReturn(tokenAfterMint);
        given(tokenModificationResult.tokenRelationship()).willReturn(treasuryRelAfterMint);

        // when:
        subject.mint(token.getId(), amount, new ArrayList<>(), Instant.now(), store);

        // then:
        verify(token).mint(treasuryRel, amount, false);
        verify(store).updateToken(tokenAfterMint);
        verify(store).updateTokenRelationship(treasuryRelAfterMint);
    }

    @Test
    void followsUniqueHappyPath() {
        treasuryRel = new TokenRelationship(token, treasury);
        final var consensusTimestamp = Instant.now();

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

        final var metadataList = List.of(
                ByteString.fromHex(
                        "00000004000000001000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000100000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000004000000000000000000000000000000000000000000000040000000000000000000000000000000001000000000000000000000000000000000000000000000001000000000000100000080000000000000000000000000000000000000000000000000000000000000000000000000"));
        given(token.mint(treasuryRel, metadataList, fromJava(consensusTimestamp)))
                .willReturn(tokenModificationResult);
        given(tokenModificationResult.token()).willReturn(tokenAfterMint);
        given(tokenModificationResult.tokenRelationship()).willReturn(treasuryRelAfterMint);
        given(tokenAfterMint.getTreasury()).willReturn(treasury);
        // when:
        subject.mint(token.getId(), 0, metadataList, consensusTimestamp, store);

        // then:
        verify(token).mint(eq(treasuryRel), eq(metadataList), any(RichInstant.class));
        verify(store).updateToken(tokenAfterMint);
        verify(store).updateTokenRelationship(treasuryRelAfterMint);
        verify(store).updateAccount(any(Account.class));
    }

    @Test
    void precheckWorksForZeroFungibleAmount() {
        givenValidTxnCtxWithZeroAmount();
        assertEquals(OK, subject.validateSyntax(tokenMintTxn));
    }

    @Test
    void precheckWorksForNonZeroFungibleAmount() {
        givenUniqueTxnCtxWithNoSerials();
        assertEquals(OK, subject.validateSyntax(tokenMintTxn));
    }

    private void givenValidUniqueTxnCtx() {
        tokenMintTxn = TransactionBody.newBuilder()
                .setTokenMint(TokenMintTransactionBody.newBuilder()
                        .setToken(grpcId)
                        .addAllMetadata(List.of(ByteString.copyFromUtf8("memo"))))
                .build();
    }

    private void givenValidTxnCtx() {
        tokenMintTxn = TransactionBody.newBuilder()
                .setTokenMint(
                        TokenMintTransactionBody.newBuilder().setToken(grpcId).setAmount(amount))
                .build();
    }

    private void givenValidTxnCtxWithZeroAmount() {
        tokenMintTxn = TransactionBody.newBuilder()
                .setTokenMint(
                        TokenMintTransactionBody.newBuilder().setToken(grpcId).setAmount(0))
                .build();
    }

    private void givenUniqueTxnCtxWithNoSerials() {
        tokenMintTxn = TransactionBody.newBuilder()
                .setTokenMint(
                        TokenMintTransactionBody.newBuilder().setToken(grpcId).addAllMetadata(List.of()))
                .build();
    }
}
