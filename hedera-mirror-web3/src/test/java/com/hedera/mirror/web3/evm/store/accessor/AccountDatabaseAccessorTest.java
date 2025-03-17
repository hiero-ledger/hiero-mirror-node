// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store.accessor;

import static com.hedera.mirror.common.util.CommonUtils.DEFAULT_TREASURY_ACCOUNT;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdNumFromEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.web3.repository.AccountBalanceRepository;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDatabaseAccessorTest {
    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final long SHARD = 0L;
    private static final long REALM = 1L;
    private static final long EXPIRATION_TIMESTAMP = 2_000_000_000L;
    private static final long BALANCE = 3L;
    private static final long AUTO_RENEW_PERIOD = 4_000_000_000L;
    private static final EntityId PROXY_ACCOUNT_ID = EntityId.of(SHARD, REALM, 5L);
    private static final int MAX_AUTOMATIC_TOKEN_ASSOCIATIONS = 6;
    private static final Optional<Long> timestamp = Optional.of(1234L);
    private static final int POSITIVE_BALANCES = 7;
    private static final int NEGATIVE_BALANCES = 8;
    private static final List<TokenAccountAssociationsCount> associationsCount = Arrays.asList(
            new TokenAccountAssociationsCount() {
                @Override
                public Integer getTokenCount() {
                    return POSITIVE_BALANCES;
                }

                @Override
                public boolean getIsPositiveBalance() {
                    return true;
                }
            },
            new TokenAccountAssociationsCount() {
                @Override
                public Integer getTokenCount() {
                    return NEGATIVE_BALANCES;
                }

                @Override
                public boolean getIsPositiveBalance() {
                    return false;
                }
            });

    private Entity entity;

    @InjectMocks
    private AccountDatabaseAccessor accountAccessor;

    @Mock
    private CommonProperties commonProperties;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private NftAllowanceRepository nftAllowanceRepository;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private TokenAllowanceRepository tokenAllowanceRepository;

    @Mock
    private CryptoAllowanceRepository cryptoAllowanceRepository;

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    @BeforeEach
    void setup() {
        final var entityNum = entityIdNumFromEvmAddress(ADDRESS);
        entity = new Entity();
        entity.setId(entityNum);
        entity.setCreatedTimestamp(timestamp.get());
        entity.setShard(SHARD);
        entity.setRealm(REALM);
        entity.setNum(entityNum);
        entity.setExpirationTimestamp(EXPIRATION_TIMESTAMP);
        entity.setBalance(BALANCE);
        entity.setDeleted(false);
        entity.setAutoRenewPeriod(AUTO_RENEW_PERIOD);
        entity.setProxyAccountId(PROXY_ACCOUNT_ID);
        entity.setMaxAutomaticTokenAssociations(MAX_AUTOMATIC_TOKEN_ASSOCIATIONS);
        entity.setType(EntityType.ACCOUNT);
    }

    @Test
    void accountFieldsMatchEntityFields() {
        when(entityDatabaseAccessor.get(ADDRESS, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        assertThat(accountAccessor.get(ADDRESS, Optional.empty())).hasValueSatisfying(account -> assertThat(account)
                .returns(new Id(entity.getShard(), entity.getRealm(), entity.getNum()), Account::getId)
                .returns(
                        TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                        Account::getExpiry)
                .returns(entity.getBalance(), Account::getBalance)
                .returns(entity.getAutoRenewPeriod(), Account::getAutoRenewSecs)
                .returns(
                        new Id(
                                entity.getProxyAccountId().getShard(),
                                entity.getProxyAccountId().getRealm(),
                                entity.getProxyAccountId().getNum()),
                        Account::getProxy)
                .returns(entity.getMaxAutomaticTokenAssociations(), Account::getMaxAutoAssociations));
    }

    @Test
    void whenExpirationTimestampIsNullThenExpiryIsBasedOnCreatedAndRenewTimestamps() {
        when(entityDatabaseAccessor.get(ADDRESS, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        entity.setExpirationTimestamp(null);
        entity.setCreatedTimestamp(987_000_000L);
        long expectedExpiry = TimeUnit.SECONDS.convert(entity.getCreatedTimestamp(), TimeUnit.NANOSECONDS)
                + entity.getAutoRenewPeriod();

        assertThat(accountAccessor.get(ADDRESS, Optional.empty()))
                .hasValueSatisfying(account -> assertThat(account).returns(expectedExpiry, Account::getExpiry));
    }

    @Test
    void useDefaultValuesWhenFieldsAreNull() {
        when(entityDatabaseAccessor.get(ADDRESS, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        entity.setExpirationTimestamp(null);
        entity.setCreatedTimestamp(null);
        entity.setAutoRenewPeriod(null);
        entity.setMaxAutomaticTokenAssociations(null);
        entity.setBalance(null);
        entity.setDeleted(null);
        entity.setProxyAccountId(null);

        assertThat(accountAccessor.get(ADDRESS, Optional.empty())).hasValueSatisfying(account -> assertThat(account)
                .returns(
                        TimeUnit.SECONDS.convert(AbstractEntity.DEFAULT_EXPIRY_TIMESTAMP, TimeUnit.NANOSECONDS),
                        Account::getExpiry)
                .returns(0L, Account::getBalance)
                .returns(false, Account::isDeleted)
                .returns(AccountDatabaseAccessor.DEFAULT_AUTO_RENEW_PERIOD, Account::getAutoRenewSecs)
                .returns(0, Account::getMaxAutoAssociations)
                .returns(null, Account::getProxy)
                .returns(0, Account::getUsedAutoAssociations));
    }

    @Test
    void accountOwnedNftsMatchesValueFromRepository() {
        when(entityDatabaseAccessor.get(ADDRESS, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        long ownedNfts = 20;
        when(nftRepository.countByAccountIdNotDeleted(any())).thenReturn(ownedNfts);

        verify(nftRepository, never()).countByAccountIdNotDeleted(entity.getId());

        assertThat(accountAccessor.get(ADDRESS, Optional.empty()))
                .hasValueSatisfying(account -> assertThat(account).returns(ownedNfts, Account::getOwnedNfts));

        verify(nftRepository).countByAccountIdNotDeleted(entity.getId());
    }

    @Test
    void accountOwnedNftsMatchesValueFromRepositoryHistorical() {
        when(entityDatabaseAccessor.get(ADDRESS, timestamp)).thenReturn(Optional.ofNullable(entity));
        long ownedNfts = 20;
        when(nftRepository.countByAccountIdAndTimestampNotDeleted(entity.getId(), timestamp.get()))
                .thenReturn(ownedNfts);

        verify(nftRepository, never()).countByAccountIdAndTimestampNotDeleted(entity.getId(), timestamp.get());

        assertThat(accountAccessor.get(ADDRESS, timestamp))
                .hasValueSatisfying(account -> assertThat(account).returns(ownedNfts, Account::getOwnedNfts));

        verify(nftRepository).countByAccountIdAndTimestampNotDeleted(entity.getId(), timestamp.get());
    }

    @Test
    void accountBalanceMatchesValueFromRepositoryHistorical() {
        when(entityDatabaseAccessor.get(ADDRESS, timestamp)).thenReturn(Optional.of(entity));
        long balance = 20;
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        entity.getId(), timestamp.get(), DEFAULT_TREASURY_ACCOUNT.getId()))
                .thenReturn(Optional.of(balance));

        assertThat(accountAccessor.get(ADDRESS, timestamp))
                .hasValueSatisfying(account -> assertThat(account).returns(balance, Account::getBalance));

        verify(accountBalanceRepository)
                .findHistoricalAccountBalanceUpToTimestamp(
                        entity.getId(), timestamp.get(), DEFAULT_TREASURY_ACCOUNT.getId());
    }

    @Test
    void accountBalanceBeforeAccountCreation() {
        entity.setCreatedTimestamp(timestamp.get() + 1);
        when(entityDatabaseAccessor.get(ADDRESS, timestamp)).thenReturn(Optional.ofNullable(entity));
        long balance = 0;

        assertThat(accountAccessor.get(ADDRESS, timestamp))
                .hasValueSatisfying(account -> assertThat(account).returns(balance, Account::getBalance));
    }

    @Test
    void accountBalanceIsZeroHistorical() {
        entity.setCreatedTimestamp(timestamp.get() - 1);
        when(entityDatabaseAccessor.get(ADDRESS, timestamp)).thenReturn(Optional.of(entity));
        long balance = 0;
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        entity.getId(), timestamp.get(), DEFAULT_TREASURY_ACCOUNT.getId()))
                .thenReturn(Optional.of(balance));

        assertThat(accountAccessor.get(ADDRESS, timestamp))
                .hasValueSatisfying(account -> assertThat(account).returns(balance, Account::getBalance));

        verify(accountBalanceRepository)
                .findHistoricalAccountBalanceUpToTimestamp(
                        entity.getId(), timestamp.get(), DEFAULT_TREASURY_ACCOUNT.getId());
    }

    @Test
    void accountBalanceWhenCreatedTimestampIsNull() {
        when(entityDatabaseAccessor.get(ADDRESS, timestamp)).thenReturn(Optional.ofNullable(entity));
        long balance = 20;
        entity.setCreatedTimestamp(null);
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        entity.getId(), timestamp.get(), DEFAULT_TREASURY_ACCOUNT.getId()))
                .thenReturn(Optional.of(balance));

        assertThat(accountAccessor.get(ADDRESS, timestamp))
                .hasValueSatisfying(account -> assertThat(account).returns(balance, Account::getBalance));
        verify(accountBalanceRepository)
                .findHistoricalAccountBalanceUpToTimestamp(
                        entity.getId(), timestamp.get(), DEFAULT_TREASURY_ACCOUNT.getId());
    }

    @Test
    void cryptoAllowancesMatchValuesFromRepository() {
        when(entityDatabaseAccessor.get(ADDRESS, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        var firstAllowance = new CryptoAllowance();
        firstAllowance.setSpender(123L);
        firstAllowance.setOwner(entity.getId());
        firstAllowance.setAmount(50L);

        CryptoAllowance secondAllowance = new CryptoAllowance();
        secondAllowance.setSpender(234L);
        secondAllowance.setOwner(entity.getId());
        secondAllowance.setAmount(60L);

        when(cryptoAllowanceRepository.findByOwner(anyLong()))
                .thenReturn(Arrays.asList(firstAllowance, secondAllowance));

        SortedMap<EntityNum, Long> allowancesMap = new TreeMap<>();

        allowancesMap.put(entityNumFromId(firstAllowance.getSpender()), firstAllowance.getAmount());
        allowancesMap.put(entityNumFromId(secondAllowance.getSpender()), secondAllowance.getAmount());

        verify(cryptoAllowanceRepository, never()).findByOwner(entity.getId());

        assertThat(accountAccessor.get(ADDRESS, Optional.empty())).hasValueSatisfying(account -> assertThat(account)
                .returns(allowancesMap, Account::getCryptoAllowances));

        verify(cryptoAllowanceRepository).findByOwner(entity.getId());
    }

    @Test
    void cryptoAllowancesMatchValuesFromRepositoryHistorical() {
        when(entityDatabaseAccessor.get(ADDRESS, timestamp)).thenReturn(Optional.ofNullable(entity));
        CryptoAllowance firstAllowance = new CryptoAllowance();
        firstAllowance.setSpender(123L);
        firstAllowance.setOwner(entity.getId());
        firstAllowance.setAmount(50L);

        CryptoAllowance secondAllowance = new CryptoAllowance();
        secondAllowance.setSpender(234L);
        secondAllowance.setOwner(entity.getId());
        secondAllowance.setAmount(60L);

        when(cryptoAllowanceRepository.findByOwnerAndTimestamp(entity.getId(), timestamp.get()))
                .thenReturn(Arrays.asList(firstAllowance, secondAllowance));

        SortedMap<EntityNum, Long> allowancesMap = new TreeMap<>();
        allowancesMap.put(entityNumFromId(firstAllowance.getSpender()), firstAllowance.getAmount());
        allowancesMap.put(entityNumFromId(secondAllowance.getSpender()), secondAllowance.getAmount());

        verify(cryptoAllowanceRepository, never()).findByOwnerAndTimestamp(entity.getId(), timestamp.get());

        assertThat(accountAccessor.get(ADDRESS, timestamp)).hasValueSatisfying(account -> assertThat(account)
                .returns(allowancesMap, Account::getCryptoAllowances));

        verify(cryptoAllowanceRepository).findByOwnerAndTimestamp(entity.getId(), timestamp.get());
    }

    @Test
    void fungibleTokenAllowancesMatchValuesFromRepository() {
        when(entityDatabaseAccessor.get(ADDRESS, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        TokenAllowance firstAllowance = new TokenAllowance();
        firstAllowance.setOwner(entity.getId());
        firstAllowance.setTokenId(15L);
        firstAllowance.setSpender(123L);
        firstAllowance.setAmount(50L);

        TokenAllowance secondAllowance = new TokenAllowance();
        secondAllowance.setOwner(entity.getId());
        secondAllowance.setTokenId(16L);
        secondAllowance.setSpender(234L);
        secondAllowance.setAmount(60L);

        when(tokenAllowanceRepository.findByOwner(entity.getId()))
                .thenReturn(Arrays.asList(firstAllowance, secondAllowance));

        SortedMap<FcTokenAllowanceId, Long> allowancesMap = new TreeMap<>();
        allowancesMap.put(
                new FcTokenAllowanceId(
                        entityNumFromId(firstAllowance.getTokenId()), entityNumFromId(firstAllowance.getSpender())),
                firstAllowance.getAmount());
        allowancesMap.put(
                new FcTokenAllowanceId(
                        entityNumFromId(secondAllowance.getTokenId()), entityNumFromId(secondAllowance.getSpender())),
                secondAllowance.getAmount());

        verify(tokenAllowanceRepository, never()).findByOwner(entity.getId());

        assertThat(accountAccessor.get(ADDRESS, Optional.empty())).hasValueSatisfying(account -> assertThat(account)
                .returns(allowancesMap, Account::getFungibleTokenAllowances));

        verify(tokenAllowanceRepository).findByOwner(entity.getId());
    }

    @Test
    void fungibleTokenAllowancesMatchValuesFromRepositoryHistorical() {
        when(entityDatabaseAccessor.get(ADDRESS, timestamp)).thenReturn(Optional.ofNullable(entity));
        TokenAllowance firstAllowance = new TokenAllowance();
        firstAllowance.setOwner(entity.getId());
        firstAllowance.setTokenId(15L);
        firstAllowance.setSpender(123L);
        firstAllowance.setAmount(50L);

        TokenAllowance secondAllowance = new TokenAllowance();
        secondAllowance.setOwner(entity.getId());
        secondAllowance.setTokenId(16L);
        secondAllowance.setSpender(234L);
        secondAllowance.setAmount(60L);

        when(tokenAllowanceRepository.findByOwnerAndTimestamp(entity.getId(), timestamp.get()))
                .thenReturn(Arrays.asList(firstAllowance, secondAllowance));

        SortedMap<FcTokenAllowanceId, Long> allowancesMap = new TreeMap<>();
        allowancesMap.put(
                new FcTokenAllowanceId(
                        entityNumFromId(firstAllowance.getTokenId()), entityNumFromId(firstAllowance.getSpender())),
                firstAllowance.getAmount());
        allowancesMap.put(
                new FcTokenAllowanceId(
                        entityNumFromId(secondAllowance.getTokenId()), entityNumFromId(secondAllowance.getSpender())),
                secondAllowance.getAmount());

        verify(tokenAllowanceRepository, never()).findByOwnerAndTimestamp(entity.getId(), timestamp.get());

        assertThat(accountAccessor.get(ADDRESS, timestamp)).hasValueSatisfying(account -> assertThat(account)
                .returns(allowancesMap, Account::getFungibleTokenAllowances));

        verify(tokenAllowanceRepository).findByOwnerAndTimestamp(entity.getId(), timestamp.get());
    }

    @Test
    void approveForAllNftsMatchValuesFromRepository() {
        when(entityDatabaseAccessor.get(ADDRESS, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        NftAllowance firstAllowance = new NftAllowance();
        firstAllowance.setOwner(entity.getId());
        firstAllowance.setTokenId(15L);
        firstAllowance.setSpender(123L);

        NftAllowance secondAllowance = new NftAllowance();
        secondAllowance.setOwner(entity.getId());
        secondAllowance.setTokenId(16L);
        secondAllowance.setSpender(234L);

        when(nftAllowanceRepository.findByOwnerAndApprovedForAllIsTrue(entity.getId()))
                .thenReturn(Arrays.asList(firstAllowance, secondAllowance));

        SortedSet<FcTokenAllowanceId> allowancesSet = new TreeSet<>();
        allowancesSet.add(new FcTokenAllowanceId(
                entityNumFromId(firstAllowance.getTokenId()), entityNumFromId(firstAllowance.getSpender())));
        allowancesSet.add(new FcTokenAllowanceId(
                entityNumFromId(secondAllowance.getTokenId()), entityNumFromId(secondAllowance.getSpender())));

        verify(nftAllowanceRepository, never()).findByOwnerAndApprovedForAllIsTrue(entity.getId());

        assertThat(accountAccessor.get(ADDRESS, Optional.empty())).hasValueSatisfying(account -> assertThat(account)
                .returns(allowancesSet, Account::getApproveForAllNfts));

        verify(nftAllowanceRepository).findByOwnerAndApprovedForAllIsTrue(entity.getId());
    }

    @Test
    void numTokenAssociationsAndNumPositiveBalancesMatchValuesFromRepository() {
        when(entityDatabaseAccessor.get(ADDRESS, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        when(tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(anyLong()))
                .thenReturn(associationsCount);

        verify(tokenAccountRepository, never()).countByAccountIdAndAssociatedGroupedByBalanceIsPositive(entity.getId());

        assertThat(accountAccessor.get(ADDRESS, Optional.empty())).hasValueSatisfying(account -> assertThat(account)
                .returns(POSITIVE_BALANCES + NEGATIVE_BALANCES, Account::getNumAssociations)
                .returns(POSITIVE_BALANCES, Account::getNumPositiveBalances));

        verify(tokenAccountRepository, times(1))
                .countByAccountIdAndAssociatedGroupedByBalanceIsPositive(entity.getId());
    }

    private EntityNum entityNumFromId(long id) {
        return EntityNum.fromEntityId(EntityId.of(id));
    }
}
