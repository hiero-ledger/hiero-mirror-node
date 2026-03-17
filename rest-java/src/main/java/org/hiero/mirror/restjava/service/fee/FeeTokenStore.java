// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.restjava.repository.CustomFeeRepository;
import org.hiero.mirror.restjava.repository.TokenRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

@Named
@RequiredArgsConstructor
public final class FeeTokenStore implements ReadableTokenStore {

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
        return tokenRepository
                .findById(id.tokenNum())
                .map(token -> toTokenMetadata(token, customFeeRepository))
                .orElse(null);
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

    private static TokenMetadata toTokenMetadata(
            final org.hiero.mirror.common.domain.token.Token token, final CustomFeeRepository customFeeRepository) {
        final var customFees = customFeeRepository.findById(token.getTokenId());
        final var hasRoyaltyWithFallback = customFees
                .map(customFee -> !CollectionUtils.isEmpty(customFee.getRoyaltyFees())
                        && customFee.getRoyaltyFees().stream()
                                .anyMatch(royaltyFee -> royaltyFee.getFallbackFee() != null))
                .orElse(false);
        return new TokenMetadata(
                null, // adminKey is not stored on AbstractToken
                parseKey(token.getKycKey()),
                parseKey(token.getWipeKey()),
                parseKey(token.getFreezeKey()),
                parseKey(token.getSupplyKey()),
                parseKey(token.getFeeScheduleKey()),
                parseKey(token.getPauseKey()),
                token.getSymbol(),
                hasRoyaltyWithFallback,
                toAccountId(token.getTreasuryAccountId()),
                token.getDecimals() != null ? token.getDecimals() : 0);
    }

    // Calculator only checks isEmpty(); CustomFee.DEFAULT is a safe placeholder.
    private static List<CustomFee> getCustomFees(long tokenId, CustomFeeRepository customFeeRepository) {
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

    private static int size(@Nullable Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    @Nullable
    private static Key parseKey(final byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length == 0) {
            return null;
        }
        try {
            return Key.PROTOBUF.parse(Bytes.wrap(keyBytes));
        } catch (ParseException e) {
            return null;
        }
    }

    @Nullable
    private static AccountID toAccountId(@Nullable final EntityId entityId) {
        if (entityId == null) {
            return null;
        }
        return AccountID.newBuilder()
                .shardNum(entityId.getShard())
                .realmNum(entityId.getRealm())
                .accountNum(entityId.getNum())
                .build();
    }
}
