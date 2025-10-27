// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static org.hiero.mirror.common.domain.entity.EntityType.TOKEN;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenPauseStatusEnum;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.evm.exception.WrongTypeException;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenRepository;
import org.hiero.mirror.web3.utils.Suppliers;
import org.jspecify.annotations.NonNull;

@Named
@RequiredArgsConstructor
public class TokenDatabaseAccessor extends DatabaseAccessor<Object, Token> {

    private final TokenRepository tokenRepository;
    private final EntityDatabaseAccessor entityDatabaseAccessor;
    private final EntityRepository entityRepository;
    private final CustomFeeDatabaseAccessor customFeeDatabaseAccessor;
    private final NftRepository nftRepository;
    private final SystemEntity systemEntity;

    @Override
    public @NonNull Optional<Token> get(@NonNull Object key, final Optional<Long> timestamp) {
        return entityDatabaseAccessor.get(key, timestamp).map(entity -> tokenFromEntity(entity, timestamp));
    }

    private Token tokenFromEntity(Entity entity, final Optional<Long> timestamp) {
        if (!TOKEN.equals(entity.getType())) {
            throw new WrongTypeException("Trying to map token from a different type");
        }
        final var databaseToken = timestamp
                .flatMap(t -> tokenRepository.findByTokenIdAndTimestamp(entity.getId(), t))
                .orElseGet(() -> tokenRepository.findById(entity.getId()).orElse(null));

        if (databaseToken == null) {
            return null;
        }
        return new Token(
                entity.getId(),
                new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                false,
                Optional.ofNullable(databaseToken.getType())
                        .map(t -> TokenType.valueOf(t.name()))
                        .orElse(null),
                Optional.ofNullable(databaseToken.getSupplyType())
                        .map(st -> TokenSupplyType.valueOf(st.name()))
                        .orElse(null),
                getTotalSupply(databaseToken, timestamp),
                databaseToken.getMaxSupply(),
                parseJkey(databaseToken.getKycKey()),
                parseJkey(databaseToken.getFreezeKey()),
                parseJkey(databaseToken.getSupplyKey()),
                parseJkey(databaseToken.getWipeKey()),
                parseJkey(entity.getKey()),
                parseJkey(databaseToken.getFeeScheduleKey()),
                parseJkey(databaseToken.getPauseKey()),
                Boolean.TRUE.equals(databaseToken.getFreezeDefault()),
                getTreasury(databaseToken.getTreasuryAccountId(), timestamp),
                getAutoRenewAccount(entity.getAutoRenewAccountId(), timestamp),
                Optional.ofNullable(entity.getDeleted()).orElse(false),
                TokenPauseStatusEnum.PAUSED.equals(databaseToken.getPauseStatus()),
                false,
                TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                entity.getCreatedTimestamp() != null
                        ? TimeUnit.SECONDS.convert(entity.getCreatedTimestamp(), TimeUnit.NANOSECONDS)
                        : 0L,
                false,
                entity.getMemo(),
                databaseToken.getName(),
                databaseToken.getSymbol(),
                Optional.ofNullable(databaseToken.getDecimals()).orElse(0),
                Optional.ofNullable(entity.getAutoRenewPeriod()).orElse(0L),
                0L,
                getCustomFees(entity.getId(), timestamp));
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

    private JKey parseJkey(byte[] keyBytes) {
        try {
            return keyBytes == null ? null : asFcKeyUnchecked(Key.parseFrom(keyBytes));
        } catch (InvalidProtocolBufferException | IllegalArgumentException e) {
            return null;
        }
    }

    private Supplier<Account> getAutoRenewAccount(Long autoRenewAccountId, final Optional<Long> timestamp) {
        if (autoRenewAccountId == null) {
            return null;
        }
        return Suppliers.memoize(() -> timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(autoRenewAccountId, t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(autoRenewAccountId))
                .map(autoRenewAccount -> new Account(
                        autoRenewAccount.getEvmAddress() != null
                                ? DomainUtils.fromBytes(autoRenewAccount.getEvmAddress())
                                : ByteString.EMPTY,
                        autoRenewAccount.getId(),
                        new Id(autoRenewAccount.getShard(), autoRenewAccount.getRealm(), autoRenewAccount.getNum()),
                        autoRenewAccount.getBalance() != null ? autoRenewAccount.getBalance() : 0L))
                .orElse(null));
    }

    private Supplier<Account> getTreasury(EntityId treasuryId, final Optional<Long> timestamp) {
        if (treasuryId == null) {
            return null;
        }
        return Suppliers.memoize(() -> timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(treasuryId.getId(), t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(treasuryId.getId()))
                .map(entity -> new Account(
                        entity.getEvmAddress() != null
                                ? DomainUtils.fromBytes(entity.getEvmAddress())
                                : ByteString.EMPTY,
                        entity.getId(),
                        new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                        entity.getBalance() != null ? entity.getBalance() : 0L))
                .orElse(null));
    }

    private Supplier<List<CustomFee>> getCustomFees(Long tokenId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> Collections.unmodifiableList(
                customFeeDatabaseAccessor.get(tokenId, timestamp).orElse(new ArrayList<>())));
    }
}
