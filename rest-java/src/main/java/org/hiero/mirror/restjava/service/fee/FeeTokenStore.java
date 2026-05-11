// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import jakarta.inject.Named;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.restjava.repository.CustomFeeRepository;
import org.hiero.mirror.restjava.repository.TokenRepository;

/**
 * Live token + custom fee lookup from mirror DB for fee estimation (STATE mode).
 */
@Named
public final class FeeTokenStore {

    private final TokenRepository tokenRepository;
    private final CustomFeeRepository customFeeRepository;

    public FeeTokenStore(TokenRepository tokenRepository, CustomFeeRepository customFeeRepository) {
        this.tokenRepository = tokenRepository;
        this.customFeeRepository = customFeeRepository;
    }

    /**
     * Returns token fee view for the given id, or {@code null} if the token does not exist.
     */
    public TokenFeeView get(TokenID tokenId) {
        long num = tokenId.tokenNum();
        var tokenOpt = tokenRepository.findById(num);
        if (tokenOpt.isEmpty()) {
            return null;
        }
        var token = tokenOpt.get();
        TokenType pbjType = token.getType() == TokenTypeEnum.NON_FUNGIBLE_UNIQUE
                ? TokenType.NON_FUNGIBLE_UNIQUE
                : TokenType.FUNGIBLE_COMMON;

        List<com.hedera.hapi.node.transaction.CustomFee> fees = List.of();
        Optional<org.hiero.mirror.common.domain.token.CustomFee> cfOpt = customFeeRepository.findById(num);
        if (cfOpt.isPresent()) {
            fees = DomainCustomFeeMapping.toPbjCustomFees(cfOpt.get());
        }
        return new TokenFeeView(tokenId, pbjType, fees);
    }

    /** Hook for Hedera throttle/state sizing; mirror-backed store does not contribute in-process state size. */
    public long sizeOfState() {
        return 0L;
    }
}
