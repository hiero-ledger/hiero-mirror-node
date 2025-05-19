// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hiero.mirror.web3.evm.store.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnpauseLogicTest {
    private final Id id = new Id(1, 2, 3);
    private final TokenID tokenID = IdUtils.asToken("1.2.3");

    @Mock
    private Token token;

    @Mock
    private Token unpausedToken;

    @Mock
    private Store store;

    private UnpauseLogic subject;

    @BeforeEach
    void setup() {
        subject = new UnpauseLogic();
    }

    @Test
    void followsHappyPathForUnpausing() {
        // given:
        given(token.getId()).willReturn(id);
        given(store.loadPossiblyPausedToken(id.asEvmAddress())).willReturn(token);
        given(token.changePauseStatus(false)).willReturn(unpausedToken);

        // when:
        subject.unpause(token.getId(), store);

        // then:
        verify(token).changePauseStatus(false);
        verify(store).updateToken(unpausedToken);
    }

    @Test
    void validateSyntaxWithCorrectBody() {
        final var builder = TokenUnpauseTransactionBody.newBuilder();
        builder.setToken(tokenID);
        final var txnBody =
                TransactionBody.newBuilder().setTokenUnpause(builder).build();

        assertEquals(ResponseCodeEnum.OK, subject.validateSyntax(txnBody));
    }

    @Test
    void validateSyntaxWithWrongBody() {
        final var builder = TokenUnpauseTransactionBody.newBuilder();
        final var txnBody =
                TransactionBody.newBuilder().setTokenUnpause(builder).build();

        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, subject.validateSyntax(txnBody));
    }

    @Test
    void rejectChangePauseStatusWithoutTokenPauseKey() {
        final Token emptyToken = Token.getEmptyToken();

        // given:
        given(store.loadPossiblyPausedToken(asTypedEvmAddress(IdUtils.asToken("1.2.3"))))
                .willReturn(emptyToken);

        // expect:
        assertFalse(emptyToken.hasPauseKey());
        assertFailsWith(() -> subject.unpause(id, store), TOKEN_HAS_NO_PAUSE_KEY);

        // verify:
        verify(store, never()).updateToken(emptyToken);
    }
}
