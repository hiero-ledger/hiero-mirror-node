// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static com.hedera.services.utils.EntityIdUtils.toTokenId;
import static org.hiero.mirror.web3.state.Utils.DEFAULT_AUTO_RENEW_PERIOD;

import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.CustomFee.FeeOneOfType;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.CustomFeeRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenRepository;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.state.Utils;
import org.hiero.mirror.web3.utils.Suppliers;
import org.jspecify.annotations.NonNull;
import org.springframework.util.CollectionUtils;

@Named
public class TokenReadableKVState extends AbstractReadableKVState<TokenID, Token> {

    public static final String KEY = "TOKENS";

    private final CommonEntityAccessor commonEntityAccessor;
    private final CustomFeeRepository customFeeRepository;
    private final TokenRepository tokenRepository;
    private final NftRepository nftRepository;
    private final SystemEntity systemEntity;

    protected TokenReadableKVState(
            final CommonEntityAccessor commonEntityAccessor,
            final CustomFeeRepository customFeeRepository,
            final TokenRepository tokenRepository,
            final EntityRepository entityRepository,
            final NftRepository nftRepository,
            final SystemEntity systemEntity) {
        super(TokenService.NAME, KEY);
        this.commonEntityAccessor = commonEntityAccessor;
        this.customFeeRepository = customFeeRepository;
        this.tokenRepository = tokenRepository;
        this.nftRepository = nftRepository;
        this.systemEntity = systemEntity;
    }

    @Override
    protected Token readFromDataSource(@NonNull TokenID key) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var entity = commonEntityAccessor.get(key, timestamp).orElse(null);

        if (entity == null || entity.getType() != EntityType.TOKEN) {
            return null;
        }

        final var token = timestamp
                .flatMap(t -> tokenRepository.findByTokenIdAndTimestamp(entity.getId(), t))
                .orElseGet(() -> tokenRepository.findById(entity.getId()).orElse(null));

        if (token == null) {
            return null;
        }

        return tokenFromEntities(entity, token, timestamp);
    }

    private Token tokenFromEntities(
            final Entity entity,
            final org.hiero.mirror.common.domain.token.Token token,
            final Optional<Long> timestamp) {
        return Token.newBuilder()
                .accountsFrozenByDefault(token.getFreezeDefault())
                .adminKey(Utils.parseKey(entity.getKey()))
                .autoRenewAccountId(EntityIdUtils.toAccountId(entity.getAutoRenewAccountId()))
                .autoRenewSeconds(
                        entity.getAutoRenewPeriod() != null ? entity.getAutoRenewPeriod() : DEFAULT_AUTO_RENEW_PERIOD)
                .customFees(getCustomFees(token.getTokenId(), timestamp))
                .decimals(token.getDecimals())
                .deleted(false)
                .expirationSecond(TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS))
                .feeScheduleKey(Utils.parseKey(token.getFeeScheduleKey()))
                .freezeKey(Utils.parseKey(token.getFreezeKey()))
                .kycKey(Utils.parseKey(token.getKycKey()))
                .maxSupply(token.getMaxSupply())
                .memo(entity.getMemo())
                .metadata(token.getMetadata() == null ? Bytes.EMPTY : Bytes.wrap(token.getMetadata()))
                .metadataKey(Utils.parseKey(token.getMetadataKey()))
                .name(token.getName())
                .paused(token.getPauseStatus().equals(TokenPauseStatusEnum.PAUSED))
                .pauseKey(Utils.parseKey(token.getPauseKey()))
                .supplyKey(Utils.parseKey(token.getSupplyKey()))
                .supplyType(TokenSupplyType.valueOf(token.getSupplyType().name()))
                .symbol(token.getSymbol())
                .tokenId(toTokenId(entity.getId()))
                .tokenType(TokenType.valueOf(token.getType().name()))
                .totalSupply(getTotalSupply(token, timestamp))
                .treasuryAccountId(EntityIdUtils.toAccountId(token.getTreasuryAccountId()))
                .wipeKey(Utils.parseKey(token.getWipeKey()))
                .accountsKycGrantedByDefault(token.getKycStatus() == TokenKycStatusEnum.GRANTED)
                .build();
    }

    private Supplier<Long> getTotalSupply(
            final org.hiero.mirror.common.domain.token.Token token, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> getTotalSupplyHistorical(
                        token.getType().equals(TokenTypeEnum.FUNGIBLE_COMMON), token.getTokenId(), t))
                .or(() -> Optional.ofNullable(token.getTotalSupply()))
                .orElse(0L));
    }

    private Long getTotalSupplyHistorical(boolean isFungible, long tokenId, long timestamp) {
        if (isFungible) {
            long treasuryAccountId = systemEntity.treasuryAccount().getId();
            return tokenRepository.findFungibleTotalSupplyByTokenIdAndTimestamp(tokenId, timestamp, treasuryAccountId);
        } else {
            return nftRepository.findNftTotalSupplyByTokenIdAndTimestamp(tokenId, timestamp);
        }
    }

    private Supplier<List<CustomFee>> getCustomFees(Long tokenId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> {
            var customFees = timestamp
                    .map(t -> customFeeRepository.findByTokenIdAndTimestamp(tokenId, t))
                    .orElseGet(() -> customFeeRepository.findById(tokenId))
                    .map(customFee -> convertCustomFees(customFee, timestamp));

            return Collections.unmodifiableList(customFees.orElseGet(ArrayList::new));
        });
    }

    private List<CustomFee> convertCustomFees(
            final org.hiero.mirror.common.domain.token.CustomFee customFees, final Optional<Long> timestamp) {
        var customFeesConstructed = new ArrayList<CustomFee>();
        customFeesConstructed.addAll(mapFixedFees(customFees, timestamp));
        customFeesConstructed.addAll(mapFractionalFees(customFees, timestamp));
        customFeesConstructed.addAll(mapRoyaltyFees(customFees, timestamp));
        return customFeesConstructed;
    }

    private List<CustomFee> mapFixedFees(
            final org.hiero.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        if (CollectionUtils.isEmpty(customFee.getFixedFees())) {
            return Collections.emptyList();
        }

        var fixedFees = new ArrayList<CustomFee>();
        customFee.getFixedFees().forEach(f -> {
            final var collector = toAccountId(commonEntityAccessor
                    .get(f.getCollectorAccountId(), timestamp)
                    .get());
            final var denominatingTokenId = f.getDenominatingTokenId();

            final var fixedFee = new FixedFee(f.getAmount(), toTokenId(denominatingTokenId));
            var constructed = new CustomFee(
                    new OneOf<>(FeeOneOfType.FIXED_FEE, fixedFee), collector, f.isAllCollectorsAreExempt());
            fixedFees.add(constructed);
        });

        return fixedFees;
    }

    private List<CustomFee> mapFractionalFees(
            final org.hiero.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        if (CollectionUtils.isEmpty(customFee.getFractionalFees())) {
            return Collections.emptyList();
        }

        var fractionalFees = new ArrayList<CustomFee>();
        customFee.getFractionalFees().forEach(f -> {
            final var collector = toAccountId(commonEntityAccessor
                    .get(f.getCollectorAccountId(), timestamp)
                    .get());
            final var fractionalFee = new FractionalFee(
                    new Fraction(f.getNumerator(), f.getDenominator()),
                    f.getMinimumAmount(),
                    f.getMaximumAmount() == null ? 0 : f.getMaximumAmount(),
                    f.isNetOfTransfers());
            var constructed = new CustomFee(
                    new OneOf<>(FeeOneOfType.FRACTIONAL_FEE, fractionalFee), collector, f.isAllCollectorsAreExempt());
            fractionalFees.add(constructed);
        });

        return fractionalFees;
    }

    private List<CustomFee> mapRoyaltyFees(
            org.hiero.mirror.common.domain.token.CustomFee customFee, final Optional<Long> timestamp) {
        if (CollectionUtils.isEmpty(customFee.getRoyaltyFees())) {
            return Collections.emptyList();
        }

        var royaltyFees = new ArrayList<CustomFee>();
        customFee.getRoyaltyFees().forEach(f -> {
            final var collector = toAccountId(commonEntityAccessor
                    .get(f.getCollectorAccountId(), timestamp)
                    .get());
            final var fallbackFee = f.getFallbackFee();

            FixedFee convertedFallbackFee = null;
            if (fallbackFee != null) {
                final var denominatingTokenId = fallbackFee.getDenominatingTokenId();
                convertedFallbackFee = new FixedFee(fallbackFee.getAmount(), toTokenId(denominatingTokenId));
            }
            final var royaltyFee =
                    new RoyaltyFee(new Fraction(f.getNumerator(), f.getDenominator()), convertedFallbackFee);
            var constructed = new CustomFee(
                    new OneOf<>(FeeOneOfType.ROYALTY_FEE, royaltyFee), collector, f.isAllCollectorsAreExempt());
            royaltyFees.add(constructed);
        });

        return royaltyFees;
    }
}
