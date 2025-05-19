// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static com.hedera.services.utils.EntityIdUtils.toTokenId;
import static org.hiero.mirror.web3.state.Utils.DEFAULT_AUTO_RENEW_PERIOD;
import static org.hiero.mirror.web3.state.Utils.EMPTY_KEY_LIST;
import static org.hiero.mirror.web3.state.Utils.parseKey;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.mirror.common.domain.SystemEntity;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.CryptoAllowanceRepository;
import org.hiero.mirror.web3.repository.NftAllowanceRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenAllowanceRepository;
import org.hiero.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import org.hiero.mirror.web3.utils.Suppliers;

public abstract class AbstractAliasedAccountReadableKVState<K, V> extends AbstractReadableKVState<K, V> {

    private final AccountBalanceRepository accountBalanceRepository;
    private final CryptoAllowanceRepository cryptoAllowanceRepository;
    private final NftAllowanceRepository nftAllowanceRepository;
    private final NftRepository nftRepository;
    private final SystemEntity systemEntity;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenAllowanceRepository tokenAllowanceRepository;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    protected AbstractAliasedAccountReadableKVState(
            @Nonnull String stateKey,
            @Nonnull AccountBalanceRepository accountBalanceRepository,
            @Nonnull CryptoAllowanceRepository cryptoAllowanceRepository,
            @Nonnull NftAllowanceRepository nftAllowanceRepository,
            @Nonnull NftRepository nftRepository,
            @Nonnull SystemEntity systemEntity,
            @Nonnull TokenAccountRepository tokenAccountRepository,
            @Nonnull TokenAllowanceRepository tokenAllowanceRepository,
            @Nonnull MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        super(stateKey);
        this.accountBalanceRepository = accountBalanceRepository;
        this.cryptoAllowanceRepository = cryptoAllowanceRepository;
        this.nftAllowanceRepository = nftAllowanceRepository;
        this.nftRepository = nftRepository;
        this.systemEntity = systemEntity;
        this.tokenAccountRepository = tokenAccountRepository;
        this.tokenAllowanceRepository = tokenAllowanceRepository;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    protected Account accountFromEntity(Entity entity, final Optional<Long> timestamp) {
        var tokenAccountBalances = getNumberOfAllAndPositiveBalanceTokenAssociations(entity.getId(), timestamp);
        byte[] alias = new byte[0];
        if (entity.getEvmAddress() != null && entity.getEvmAddress().length > 0) {
            alias = entity.getEvmAddress();
        } else if (entity.getAlias() != null && entity.getAlias().length > 0) {
            alias = entity.getAlias();
        }
        final boolean isSmartContract = CONTRACT.equals(entity.getType());

        return Account.newBuilder()
                .accountId(EntityIdUtils.toAccountId(entity.toEntityId()))
                .alias(Bytes.wrap(alias))
                .approveForAllNftAllowances(getApproveForAllNfts(entity.getId(), timestamp))
                .autoRenewAccountId(toAccountId(entity.getAutoRenewAccountId()))
                .autoRenewSeconds(Objects.requireNonNullElse(entity.getAutoRenewPeriod(), DEFAULT_AUTO_RENEW_PERIOD))
                .contractKvPairsNumber(getStorageKVPairs(entity))
                .cryptoAllowances(getCryptoAllowances(entity.getId(), timestamp))
                .deleted(Objects.requireNonNullElse(entity.getDeleted(), false))
                .ethereumNonce(Objects.requireNonNullElse(entity.getEthereumNonce(), 0L))
                .expirationSecond(TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS))
                .expiredAndPendingRemoval(false)
                .key(getKey(entity, isSmartContract))
                .maxAutoAssociations(Objects.requireNonNullElse(entity.getMaxAutomaticTokenAssociations(), 0))
                .memo(entity.getMemo())
                .numberAssociations(() -> tokenAccountBalances.get().all())
                .numberOwnedNfts(getOwnedNfts(entity.getId(), timestamp))
                .numberPositiveBalances(() -> tokenAccountBalances.get().positive())
                .receiverSigRequired(entity.getReceiverSigRequired() != null && entity.getReceiverSigRequired())
                .smartContract(isSmartContract)
                .tinybarBalance(getAccountBalance(entity, timestamp))
                .tokenAllowances(getFungibleTokenAllowances(entity.getId(), timestamp))
                .build();
    }

    private Key getKey(final Entity entity, final boolean isSmartContract) {
        final var key = parseKey(entity.getKey());
        if (key == null) {
            if (isSmartContract) {
                return Key.newBuilder()
                        .contractID(ContractID.newBuilder()
                                .shardNum(entity.getShard())
                                .realmNum(entity.getRealm())
                                .contractNum(entity.getNum())
                                .build())
                        .build();
            } else {
                // In hedera.app there isn't a case in which an account does not have a key set in the state - it is
                // either valid, or it is an empty KeyList as the one below. This key is added in the account state in
                // the mirror node for consistency as well as to prevent from potential NullPointerException.
                return EMPTY_KEY_LIST;
            }
        }
        return key;
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
    private Supplier<Long> getAccountBalance(final Entity entity, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> {
                    Long createdTimestamp = entity.getCreatedTimestamp();
                    if (createdTimestamp == null || t >= createdTimestamp) {
                        long treasuryAccountId = systemEntity.treasuryAccount().getId();
                        return accountBalanceRepository
                                .findHistoricalAccountBalanceUpToTimestamp(entity.getId(), t, treasuryAccountId)
                                .orElse(0L);
                    } else {
                        return 0L;
                    }
                })
                .orElseGet(() -> Objects.requireNonNullElse(entity.getBalance(), 0L)));
    }

    private Supplier<List<AccountCryptoAllowance>> getCryptoAllowances(
            final Long ownerId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> cryptoAllowanceRepository.findByOwnerAndTimestamp(ownerId, t))
                .orElseGet(() -> cryptoAllowanceRepository.findByOwner(ownerId))
                .stream()
                .map(this::convertCryptoAllowance)
                .toList());
    }

    private Supplier<List<AccountFungibleTokenAllowance>> getFungibleTokenAllowances(
            final Long ownerId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> tokenAllowanceRepository.findByOwnerAndTimestamp(ownerId, t))
                .orElseGet(() -> tokenAllowanceRepository.findByOwner(ownerId))
                .stream()
                .map(this::convertFungibleAllowance)
                .toList());
    }

    private Supplier<List<AccountApprovalForAllAllowance>> getApproveForAllNfts(
            final Long ownerId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> nftAllowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(ownerId, t))
                .orElseGet(() -> nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(ownerId))
                .stream()
                .map(this::convertNftAllowance)
                .toList());
    }

    private TokenAccountBalances getTokenAccountBalances(final List<TokenAccountAssociationsCount> counts) {
        int all = 0;
        int positive = 0;

        for (final var count : counts) {
            if (count.getIsPositiveBalance()) {
                positive = count.getTokenCount();
            }
            all += count.getTokenCount();
        }

        final var allAggregated = all;
        final var positiveAggregated = positive;

        return new TokenAccountBalances(allAggregated, positiveAggregated);
    }

    private AccountFungibleTokenAllowance convertFungibleAllowance(final TokenAllowance tokenAllowance) {
        return new AccountFungibleTokenAllowance(
                toTokenId(tokenAllowance.getTokenId()),
                toAccountId(tokenAllowance.getSpender()),
                tokenAllowance.getAmount());
    }

    private AccountCryptoAllowance convertCryptoAllowance(final CryptoAllowance cryptoAllowance) {
        return new AccountCryptoAllowance(toAccountId(cryptoAllowance.getSpender()), cryptoAllowance.getAmount());
    }

    private AccountApprovalForAllAllowance convertNftAllowance(final NftAllowance nftAllowance) {
        return new AccountApprovalForAllAllowance(
                toTokenId(nftAllowance.getTokenId()), toAccountId(nftAllowance.getSpender()));
    }

    private Supplier<TokenAccountBalances> getNumberOfAllAndPositiveBalanceTokenAssociations(
            long accountId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> getTokenAccountBalances(timestamp
                .map(t -> tokenAccountRepository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, t))
                .orElseGet(
                        () -> tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(accountId))
                .stream()
                .toList()));
    }

    private int getStorageKVPairs(final Entity entity) {
        if (!CONTRACT.equals(entity.getType())) {
            return 0;
        }
        final var configuration = mirrorNodeEvmProperties.getVersionedConfiguration();
        return configuration.getConfigData(ContractsConfig.class).maxKvPairsIndividual() / 2;
    }

    private record TokenAccountBalances(int all, int positive) {}
}
