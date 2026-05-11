// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.service.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import java.util.Optional;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.restjava.repository.CustomFeeRepository;
import org.hiero.mirror.restjava.repository.TokenRepository;
import org.hiero.mirror.restjava.service.fee.FeeTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class FeeTokenStoreTest {

    @InjectMocks
    private FeeTokenStore store;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private CustomFeeRepository customFeeRepository;

    private DomainBuilder domainBuilder;

    @BeforeEach
    void setup() {
        domainBuilder = new DomainBuilder();
    }

    @Test
    void getReturnsNullWhenNotFound() {
        var id = TokenID.newBuilder().tokenNum(Long.MAX_VALUE).build();
        when(tokenRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

        assertThat(store.get(id)).isNull();
    }

    @Test
    void getFungibleTokenWithoutCustomFees() {
        var token = domainBuilder.token().get();
        var id = TokenID.newBuilder().tokenNum(token.getTokenId()).build();
        when(tokenRepository.findById(token.getTokenId())).thenReturn(Optional.of(token));
        when(customFeeRepository.findById(token.getTokenId())).thenReturn(Optional.empty());

        var result = store.get(id);

        assertThat(result).isNotNull();
        assertThat(result.tokenId()).isEqualTo(id);
        assertThat(result.tokenType()).isEqualTo(TokenType.FUNGIBLE_COMMON);
        assertThat(result.customFees()).isEmpty();
    }

    @Test
    void getFungibleTokenWithCustomFees() {
        var token = domainBuilder.token().get();
        var customFee = domainBuilder
                .customFee()
                .customize(cf -> cf.entityId(token.getTokenId()))
                .get();
        var id = TokenID.newBuilder().tokenNum(token.getTokenId()).build();
        when(tokenRepository.findById(token.getTokenId())).thenReturn(Optional.of(token));
        when(customFeeRepository.findById(token.getTokenId())).thenReturn(Optional.of(customFee));

        var result = store.get(id);

        assertThat(result).isNotNull();
        assertThat(result.tokenType()).isEqualTo(TokenType.FUNGIBLE_COMMON);
        final int expectedCount =
                size(customFee.getFixedFees()) + size(customFee.getFractionalFees()) + size(customFee.getRoyaltyFees());
        assertThat(result.customFees()).hasSize(expectedCount);
    }

    @Test
    void getNftToken() {
        var token = domainBuilder
                .token()
                .customize(t -> t.type(TokenTypeEnum.NON_FUNGIBLE_UNIQUE))
                .get();
        var id = TokenID.newBuilder().tokenNum(token.getTokenId()).build();
        when(tokenRepository.findById(token.getTokenId())).thenReturn(Optional.of(token));
        when(customFeeRepository.findById(token.getTokenId())).thenReturn(Optional.empty());

        var result = store.get(id);

        assertThat(result).isNotNull();
        assertThat(result.tokenType()).isEqualTo(TokenType.NON_FUNGIBLE_UNIQUE);
    }

    @Test
    void sizeOfStateReturnsZero() {
        assertThat(store.sizeOfState()).isZero();
    }

    private static int size(java.util.Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }
}
