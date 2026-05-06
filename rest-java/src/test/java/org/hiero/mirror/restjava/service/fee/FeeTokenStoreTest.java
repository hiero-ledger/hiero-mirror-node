// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class FeeTokenStoreTest extends RestJavaIntegrationTest {

    private final FeeTokenStore store;

    @Test
    void getReturnsNullWhenNotFound() {
        var id = TokenID.newBuilder().tokenNum(Long.MAX_VALUE).build();
        assertThat(store.get(id)).isNull();
    }

    @Test
    void getFungibleTokenWithoutCustomFees() {
        var token = domainBuilder.token().persist();
        var id = TokenID.newBuilder().tokenNum(token.getTokenId()).build();

        var result = store.get(id);

        assertThat(result).isNotNull();
        assertThat(result.tokenId()).isEqualTo(id);
        assertThat(result.tokenType()).isEqualTo(TokenType.FUNGIBLE_COMMON);
        assertThat(result.customFees()).isEmpty();
    }

    @Test
    void getFungibleTokenWithCustomFees() {
        var token = domainBuilder.token().persist();
        var customFee = domainBuilder
                .customFee()
                .customize(cf -> cf.entityId(token.getTokenId()))
                .persist();
        var id = TokenID.newBuilder().tokenNum(token.getTokenId()).build();

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
                .persist();
        var id = TokenID.newBuilder().tokenNum(token.getTokenId()).build();

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
