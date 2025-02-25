// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.txn.token;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static org.junit.Assert.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.IdUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PauseLogicTest {
    private final Id id = new Id(1, 2, 3);

    @Mock
    private Token token;

    @Mock
    private Token pausedToken;

    @Mock
    private Store store;

    private PauseLogic subject;

    @BeforeEach
    void setup() {
        subject = new PauseLogic();
    }

    @Test
    void followsHappyPathForPausing() {
        // given:
        given(token.getId()).willReturn(id);
        given(store.loadPossiblyPausedToken(id.asEvmAddress())).willReturn(token);
        given(token.changePauseStatus(true)).willReturn(pausedToken);

        // when:
        subject.pause(token.getId(), store);

        // then:
        verify(token).changePauseStatus(true);
        verify(store).updateToken(pausedToken);
    }

    @Test
    void rejectChangePauseStatusWithoutTokenPauseKey() {
        final Token emptyToken = Token.getEmptyToken();

        // given:
        given(store.loadPossiblyPausedToken(asTypedEvmAddress(IdUtils.asToken("1.2.3"))))
                .willReturn(emptyToken);

        // expect:
        assertFalse(emptyToken.hasPauseKey());
        assertFailsWith(() -> subject.pause(id, store), TOKEN_HAS_NO_PAUSE_KEY);

        // verify:
        verify(store, never()).updateToken(emptyToken);
    }
}
