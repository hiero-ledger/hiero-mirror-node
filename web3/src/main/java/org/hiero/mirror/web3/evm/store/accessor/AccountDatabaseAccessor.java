// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static com.hedera.services.utils.EntityIdUtils.idFromEntityId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.web3.evm.store.accessor.TokenRelationshipDatabaseAccessor.ZERO_BALANCE;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.jproto.JContractIDKey;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.AbstractTokenAllowance;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.evm.exception.WrongTypeException;
import org.hiero.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.CryptoAllowanceRepository;
import org.hiero.mirror.web3.repository.NftAllowanceRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenAllowanceRepository;
import org.hiero.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import org.hiero.mirror.web3.utils.Suppliers;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.NonNull;

@Named
@RequiredArgsConstructor
public class AccountDatabaseAccessor extends DatabaseAccessor<Object, Account> {
    public static final long DEFAULT_AUTO_RENEW_PERIOD = 7776000L;

    private static final BinaryOperator<Long> NO_DUPLICATE_MERGE_FUNCTION = (v1, v2) -> {
        throw new IllegalStateException(String.format("Duplicate key for values %s and %s", v1, v2));
    };

    private final EntityDatabaseAccessor entityDatabaseAccessor;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftRepository nftRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final SystemEntity systemEntity;

    @Override
    public @NonNull Optional<Account> get(@NonNull Object key, final Optional<Long> timestamp) {
        if (key instanceof Address address) {
            return entityDatabaseAccessor.get(address, timestamp).map(entity -> accountFromEntity(entity, timestamp));
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(Account.class.getTypeName(), key.getClass().getTypeName()));
    }

    private Account accountFromEntity(Entity entity, final Optional<Long> timestamp) {
        if (!ACCOUNT.equals(entity.getType()) && !CONTRACT.equals(entity.getType())) {
            throw new WrongTypeException("Trying to map an account/contract from a different type");
        }

        var tokenAccountBalances = getNumberOfAllAndPositiveBalanceTokenAssociations(entity.getId(), timestamp);

        return new Account(
                entity.getEvmAddress() != null && entity.getEvmAddress().length > 0
                        ? ByteString.copyFrom(entity.getEvmAddress())
                        : ByteString.EMPTY,
                entity.getId(),
                new Id(entity.getShard(), entity.getRealm(), entity.getNum()),
                TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                getAccountBalance(entity, timestamp),
                Optional.ofNullable(entity.getDeleted()).orElse(false),
                getOwnedNfts(entity.getId(), timestamp),
                Optional.ofNullable(entity.getAutoRenewPeriod()).orElse(DEFAULT_AUTO_RENEW_PERIOD),
                idFromEntityId(entity.getProxyAccountId()),
                Optional.ofNullable(entity.getMaxAutomaticTokenAssociations()).orElse(0),
                getCryptoAllowances(entity.getId(), timestamp),
                getFungibleTokenAllowances(entity.getId(), timestamp),
                getApproveForAllNfts(entity.getId(), timestamp),
                () -> tokenAccountBalances.get().all(),
                () -> tokenAccountBalances.get().positive(),
                0,
                Optional.ofNullable(entity.getEthereumNonce()).orElse(0L),
                entity.getType().equals(CONTRACT),
                parseJkey(entity),
                entity.getCreatedTimestamp() != null
                        ? TimeUnit.SECONDS.convert(entity.getCreatedTimestamp(), TimeUnit.NANOSECONDS)
                        : 0L,
                0);
    }

    private Supplier<Long> getOwnedNfts(Long accountId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> nftRepository.countByAccountIdAndTimestampNotDeleted(accountId, t))
                .orElseGet(() -> nftRepository.countByAccountIdNotDeleted(accountId)));
    }

    /**
     * Determines account balance based on block context.
     *
     * Non-historical Call:
     * Get the balance from entity.getBalance()
     * Historical Call:
     * If the entity creation is after the passed timestamp - return 0L (the entity was not created)
     * Else get the balance from the historical query `findHistoricalAccountBalanceUpToTimestamp`
     */
    private Supplier<Long> getAccountBalance(Entity entity, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> {
                    Long createdTimestamp = entity.getCreatedTimestamp();
                    long treasuryAccountId = systemEntity.treasuryAccount().getId();
                    if (createdTimestamp == null || t >= createdTimestamp) {
                        return accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                                entity.getId(), t, treasuryAccountId);
                    } else {
                        return ZERO_BALANCE;
                    }
                })
                .orElseGet(() -> Optional.ofNullable(entity.getBalance()))
                .orElse(0L));
    }

    private Supplier<SortedMap<EntityNum, Long>> getCryptoAllowances(Long ownerId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> Collections.unmodifiableSortedMap((SortedMap<EntityNum, Long>) timestamp
                .map(t -> cryptoAllowanceRepository.findByOwnerAndTimestamp(ownerId, t))
                .orElseGet(() -> cryptoAllowanceRepository.findByOwner(ownerId))
                .stream()
                .collect(Collectors.toMap(
                        cryptoAllowance -> entityNumFromId(EntityId.of(cryptoAllowance.getSpender())),
                        CryptoAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new))));
    }

    private Supplier<SortedMap<FcTokenAllowanceId, Long>> getFungibleTokenAllowances(
            Long ownerId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> Collections.unmodifiableSortedMap((SortedMap<FcTokenAllowanceId, Long>) timestamp
                .map(t -> tokenAllowanceRepository.findByOwnerAndTimestamp(ownerId, t))
                .orElseGet(() -> tokenAllowanceRepository.findByOwner(ownerId))
                .stream()
                .collect(Collectors.toMap(
                        tokenAllowance -> new FcTokenAllowanceId(
                                entityNumFromId(EntityId.of(tokenAllowance.getTokenId())),
                                entityNumFromId(EntityId.of(tokenAllowance.getSpender()))),
                        AbstractTokenAllowance::getAmount,
                        NO_DUPLICATE_MERGE_FUNCTION,
                        TreeMap::new))));
    }

    private Supplier<SortedSet<FcTokenAllowanceId>> getApproveForAllNfts(Long ownerId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> Collections.unmodifiableSortedSet(timestamp
                .map(t -> nftAllowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(ownerId, t))
                .orElseGet(() -> nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(ownerId))
                .stream()
                .map(nftAllowance -> new FcTokenAllowanceId(
                        entityNumFromId(EntityId.of(nftAllowance.getTokenId())),
                        entityNumFromId(EntityId.of(nftAllowance.getSpender()))))
                .collect(Collectors.toCollection(TreeSet::new))));
    }

    private EntityNum entityNumFromId(EntityId entityId) {
        return EntityNum.fromEntityId(entityId);
    }

    private Supplier<TokenAccountBalances> getNumberOfAllAndPositiveBalanceTokenAssociations(
            long accountId, final Optional<Long> timestamp) {
        var counts = timestamp
                .map(t -> tokenAccountRepository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, t))
                .orElseGet(() ->
                        tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(accountId));
        int all = 0;
        int positive = 0;

        for (TokenAccountAssociationsCount count : counts) {
            if (count.getIsPositiveBalance()) {
                positive = count.getTokenCount();
            }
            all += count.getTokenCount();
        }

        final var allAggregated = all;
        final var positiveAggregated = positive;

        return Suppliers.memoize(() -> new TokenAccountBalances(allAggregated, positiveAggregated));
    }

    private JKey parseJkey(Entity entity) {
        try {
            final byte[] keyBytes = entity.getKey();
            if (keyBytes != null) {
                return asFcKeyUnchecked(Key.parseFrom(keyBytes));
            }
            if (entity.getType() == EntityType.CONTRACT) {
                return new JContractIDKey(ContractID.newBuilder()
                        .setContractNum(entity.getNum())
                        .setRealmNum(entity.getRealm())
                        .setShardNum(entity.getShard())
                        .build());
            }

            return null;
        } catch (InvalidProtocolBufferException | IllegalArgumentException e) {
            return null;
        }
    }

    private record TokenAccountBalances(int all, int positive) {}
}
