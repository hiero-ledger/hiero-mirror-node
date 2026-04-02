// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.restjava.repository.CustomFeeRepository;
import org.hiero.mirror.restjava.repository.TokenRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Named
@RequiredArgsConstructor
final class FeeTokenStore implements ReadableTokenStore {

    private final TokenRepository tokenRepository;
    private final CustomFeeRepository customFeeRepository;

    @Override
    @Nullable
    public Token get(@NonNull final TokenID id) {
        return tokenRepository
                .findById(id.tokenNum())
                .map(token -> toToken(id, token, customFeeRepository))
                .orElse(null);
    }

    @Override
    @Nullable
    public TokenMetadata getTokenMeta(@NonNull final TokenID id) {
        return null;
    }

    @Override
    public long sizeOfState() {
        return 0;
    }

    private static Token toToken(
            final TokenID id,
            final org.hiero.mirror.common.domain.token.Token token,
            final CustomFeeRepository customFeeRepository) {
        return Token.newBuilder()
                .tokenId(id)
                .tokenType(
                        token.getType() == TokenTypeEnum.NON_FUNGIBLE_UNIQUE
                                ? TokenType.NON_FUNGIBLE_UNIQUE
                                : TokenType.FUNGIBLE_COMMON)
                .customFees(getCustomFees(token.getTokenId(), customFeeRepository))
                .build();
    }

    // Calculator only checks isEmpty(); CustomFee.DEFAULT is a safe placeholder.
    private static List<CustomFee> getCustomFees(final long tokenId, final CustomFeeRepository customFeeRepository) {
        return customFeeRepository
                .findById(tokenId)
                .filter(customFee -> !customFee.isEmptyFee())
                .map(customFee -> {
                    final var count = size(customFee.getFixedFees())
                            + size(customFee.getFractionalFees())
                            + size(customFee.getRoyaltyFees());
                    return Collections.nCopies(count, CustomFee.DEFAULT);
                })
                .orElseGet(List::of);
    }

    private static int size(@Nullable final Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }
}
