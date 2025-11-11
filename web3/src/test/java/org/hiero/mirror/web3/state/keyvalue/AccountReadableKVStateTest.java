// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hiero.mirror.web3.state.Utils.EMPTY_KEY_LIST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.CryptoAllowanceRepository;
import org.hiero.mirror.web3.repository.NftAllowanceRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenAllowanceRepository;
import org.hiero.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import org.hiero.mirror.web3.state.AliasedAccountCacheManager;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountReadableKVStateTest {
    private static final long SHARD = 0L;
    private static final long REALM = 1L;
    private static final long NUM = 1252L;
    private static final long TOKEN_NUM = 1253L;
    private static final AccountID ACCOUNT_ID =
            new AccountID(SHARD, REALM, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, NUM));
    private static final AccountID ACCOUNT_ID_TOKEN =
            new AccountID(SHARD, REALM, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, TOKEN_NUM));
    private static final EntityId AUTO_RENEW_ACCOUNT_ID = EntityId.of(SHARD, REALM, NUM + 1);
    private static final long EXPIRATION_TIMESTAMP = 2_000_000_000L;
    private static final long BALANCE = 3L;
    private static final long AUTO_RENEW_PERIOD = 4_000_000_000L;
    private static final EntityId PROXY_ACCOUNT_ID = EntityId.of(SHARD, REALM, 5L);
    private static final int MAX_AUTOMATIC_TOKEN_ASSOCIATIONS = 6;
    private static final Optional<Long> timestamp = Optional.of(1234L);
    private static final int POSITIVE_BALANCES = 7;
    private static final int NEGATIVE_BALANCES = 8;
    private static final ProtoBytes EVM_ADDRESS_BYTES =
            new ProtoBytes(Bytes.wrap("67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69".getBytes()));
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

    @AutoClose
    private static final MockedStatic<ContractCallContext> contextMockedStatic = mockStatic(ContractCallContext.class);

    private Entity entity;
    private Entity token;
    private EntityId treasuryAccountId;

    @InjectMocks
    private AccountReadableKVState accountReadableKVState;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

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

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Spy
    private AliasedAccountCacheManager aliasedAccountCacheManager;

    @Spy
    private ContractCallContext contractCallContext;

    @Mock(strictness = Strictness.LENIENT)
    private SystemEntity systemEntity;

    @BeforeEach
    void setup() {
        var systemEntity = new SystemEntity(CommonProperties.getInstance());
        treasuryAccountId = systemEntity.treasuryAccount();
        entity = new Entity();
        entity.setId(EntityIdUtils.toAccountId(SHARD, REALM, NUM).accountNum());
        entity.setCreatedTimestamp(timestamp.get());
        entity.setShard(SHARD);
        entity.setRealm(REALM);
        entity.setNum(NUM);
        entity.setExpirationTimestamp(EXPIRATION_TIMESTAMP);
        entity.setBalance(BALANCE);
        entity.setDeleted(false);
        entity.setAutoRenewPeriod(AUTO_RENEW_PERIOD);
        entity.setProxyAccountId(PROXY_ACCOUNT_ID);
        entity.setAutoRenewAccountId(AUTO_RENEW_ACCOUNT_ID.getId());
        entity.setMaxAutomaticTokenAssociations(MAX_AUTOMATIC_TOKEN_ASSOCIATIONS);
        entity.setType(EntityType.ACCOUNT);
        entity.setEvmAddress(EVM_ADDRESS_BYTES.value().toByteArray());

        token = new Entity();
        token.setId(EntityIdUtils.toAccountId(SHARD, REALM, TOKEN_NUM).accountNum());
        token.setCreatedTimestamp(timestamp.get());
        token.setShard(SHARD);
        token.setRealm(REALM);
        token.setNum(TOKEN_NUM);
        token.setType(EntityType.TOKEN);

        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
        when(this.systemEntity.treasuryAccount()).thenReturn(treasuryAccountId);
    }

    @Test
    void accountFieldsMatchEntityFields() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        assertThat(accountReadableKVState.get(ACCOUNT_ID)).satisfies(account -> assertThat(account)
                .returns(
                        new AccountID(
                                entity.getShard(),
                                entity.getRealm(),
                                new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getNum())),
                        com.hedera.hapi.node.state.token.Account::accountId)
                .returns(
                        TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                        Account::expirationSecond)
                .returns(entity.getBalance(), Account::tinybarBalance)
                .returns(entity.getAutoRenewPeriod(), Account::autoRenewSeconds)
                .returns(entity.getMaxAutomaticTokenAssociations(), Account::maxAutoAssociations));
    }

    @Test
    void missingAccountKeyMatchesDefaultValues() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        assertThat(accountReadableKVState.get(ACCOUNT_ID)).satisfies(account -> assertThat(account)
                .returns(
                        new AccountID(
                                entity.getShard(),
                                entity.getRealm(),
                                new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getNum())),
                        com.hedera.hapi.node.state.token.Account::accountId)
                .returns(EMPTY_KEY_LIST, Account::key));
    }

    @Test
    void missingContractKeyMatchesDefaultValues() {
        final var contractEntity = entity.toBuilder().type(EntityType.CONTRACT).build();
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(contractEntity));
        final var mockedConfiguration = mock(VersionedConfiguration.class);
        final var mockedContractsConfig = mock(ContractsConfig.class);
        when(mirrorNodeEvmProperties.getVersionedConfiguration()).thenReturn(mockedConfiguration);
        when(mockedConfiguration.getConfigData(ContractsConfig.class)).thenReturn(mockedContractsConfig);
        when(mockedContractsConfig.maxKvPairsIndividual()).thenReturn(1);
        assertThat(accountReadableKVState.get(ACCOUNT_ID)).satisfies(account -> assertThat(account)
                .returns(
                        new AccountID(
                                contractEntity.getShard(),
                                contractEntity.getRealm(),
                                new OneOf<>(AccountOneOfType.ACCOUNT_NUM, contractEntity.getNum())),
                        com.hedera.hapi.node.state.token.Account::accountId)
                .returns(
                        Key.newBuilder()
                                .contractID(ContractID.newBuilder()
                                        .shardNum(contractEntity.getShard())
                                        .realmNum(contractEntity.getRealm())
                                        .contractNum(contractEntity.getNum())
                                        .build())
                                .build(),
                        Account::key));
    }

    @Test
    void accountIsNullWhenTheAccountIdIsToken() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID_TOKEN, Optional.empty())).thenReturn(Optional.ofNullable(token));
        assertThat(accountReadableKVState.get(ACCOUNT_ID_TOKEN)).isNull();
    }

    @Test
    void accountFieldsWithEvmAddressAliasMatchEntityFields() {
        final var evmAddress = "0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb";
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        entity.setEvmAddress(Bytes.wrap(evmAddress).toByteArray());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        assertThat(accountReadableKVState.get(ACCOUNT_ID)).satisfies(account -> assertThat(account)
                .returns(
                        new AccountID(
                                entity.getShard(),
                                entity.getRealm(),
                                new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getNum())),
                        com.hedera.hapi.node.state.token.Account::accountId)
                .returns(
                        TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                        Account::expirationSecond)
                .returns(entity.getBalance(), Account::tinybarBalance)
                .returns(entity.getAutoRenewPeriod(), Account::autoRenewSeconds)
                .returns(entity.getMaxAutomaticTokenAssociations(), Account::maxAutoAssociations)
                .returns(Bytes.wrap(entity.getEvmAddress()), Account::alias));
    }

    @Test
    void accountFieldsWithPublicKeyAliasMatchEntityFields() {
        final var ecdsaPublicKey = "0x03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d";
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        entity.setEvmAddress(null);
        entity.setAlias(Bytes.wrap(ecdsaPublicKey).toByteArray());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        assertThat(accountReadableKVState.get(ACCOUNT_ID)).satisfies(account -> assertThat(account)
                .returns(
                        new AccountID(
                                entity.getShard(),
                                entity.getRealm(),
                                new OneOf<>(AccountOneOfType.ACCOUNT_NUM, entity.getNum())),
                        com.hedera.hapi.node.state.token.Account::accountId)
                .returns(
                        TimeUnit.SECONDS.convert(entity.getEffectiveExpiration(), TimeUnit.NANOSECONDS),
                        Account::expirationSecond)
                .returns(entity.getBalance(), Account::tinybarBalance)
                .returns(entity.getAutoRenewPeriod(), Account::autoRenewSeconds)
                .returns(entity.getMaxAutomaticTokenAssociations(), Account::maxAutoAssociations)
                .returns(Bytes.wrap(entity.getAlias()), Account::alias));
    }

    @Test
    void whenExpirationTimestampIsNullThenExpiryIsBasedOnCreatedAndRenewTimestamps() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        entity.setExpirationTimestamp(null);
        entity.setCreatedTimestamp(987_000_000L);
        long expectedExpiry = TimeUnit.SECONDS.convert(entity.getCreatedTimestamp(), TimeUnit.NANOSECONDS)
                + entity.getAutoRenewPeriod();

        assertThat(accountReadableKVState.get(ACCOUNT_ID))
                .satisfies(account -> assertThat(account).returns(expectedExpiry, Account::expirationSecond));
    }

    @Test
    void useDefaultValuesWhenFieldsAreNull() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        entity.setExpirationTimestamp(null);
        entity.setCreatedTimestamp(null);
        entity.setAutoRenewPeriod(null);
        entity.setMaxAutomaticTokenAssociations(null);
        entity.setBalance(null);
        entity.setDeleted(null);
        entity.setProxyAccountId(null);

        assertThat(accountReadableKVState.get(ACCOUNT_ID)).satisfies(account -> assertThat(account)
                .returns(
                        TimeUnit.SECONDS.convert(AbstractEntity.DEFAULT_EXPIRY_TIMESTAMP, TimeUnit.NANOSECONDS),
                        Account::expirationSecond)
                .returns(0L, Account::numberOwnedNfts)
                .returns(false, Account::deleted)
                .returns(AccountDatabaseAccessor.DEFAULT_AUTO_RENEW_PERIOD, Account::autoRenewSeconds)
                .returns(0, Account::maxAutoAssociations)
                .returns(0, Account::usedAutoAssociations));
    }

    @Test
    void accountOwnedNftsMatchesValueFromRepository() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        long ownedNfts = 20;
        when(nftRepository.countByAccountIdNotDeleted(any())).thenReturn(ownedNfts);

        verify(nftRepository, never()).countByAccountIdNotDeleted(entity.getId());

        assertThat(accountReadableKVState.get(ACCOUNT_ID))
                .satisfies(account -> assertThat(account).returns(ownedNfts, Account::numberOwnedNfts));

        verify(nftRepository).countByAccountIdNotDeleted(entity.getId());
    }

    @Test
    void accountOwnedNftsMatchesValueFromRepositoryHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(ACCOUNT_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        long ownedNfts = 20;
        when(nftRepository.countByAccountIdAndTimestampNotDeleted(entity.getId(), timestamp.get()))
                .thenReturn(ownedNfts);

        verify(nftRepository, never()).countByAccountIdAndTimestampNotDeleted(entity.getId(), timestamp.get());

        assertThat(accountReadableKVState.get(ACCOUNT_ID))
                .satisfies(account -> assertThat(account).returns(ownedNfts, Account::numberOwnedNfts));

        verify(nftRepository).countByAccountIdAndTimestampNotDeleted(entity.getId(), timestamp.get());
    }

    @Test
    void accountBalanceMatchesValueFromRepositoryHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(ACCOUNT_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        long balance = 20;
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        entity.getId(), timestamp.get(), treasuryAccountId.getId()))
                .thenReturn(Optional.of(balance));

        assertThat(accountReadableKVState.get(ACCOUNT_ID)).returns(balance, Account::tinybarBalance);

        verify(accountBalanceRepository)
                .findHistoricalAccountBalanceUpToTimestamp(entity.getId(), timestamp.get(), treasuryAccountId.getId());
    }

    @Test
    void accountBalanceBeforeAccountCreation() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        entity.setCreatedTimestamp(timestamp.get() + 1);
        when(commonEntityAccessor.get(ACCOUNT_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        long balance = 0;

        assertThat(accountReadableKVState.get(ACCOUNT_ID))
                .satisfies(account -> assertThat(account).returns(balance, Account::tinybarBalance));
    }

    @Test
    void accountBalanceIsZeroHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        entity.setCreatedTimestamp(timestamp.get() - 1);
        when(commonEntityAccessor.get(ACCOUNT_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        long balance = 0;
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        entity.getId(), timestamp.get(), treasuryAccountId.getId()))
                .thenReturn(Optional.of(balance));

        assertThat(accountReadableKVState.get(ACCOUNT_ID)).returns(balance, Account::tinybarBalance);

        verify(accountBalanceRepository)
                .findHistoricalAccountBalanceUpToTimestamp(entity.getId(), timestamp.get(), treasuryAccountId.getId());
    }

    @Test
    void accountBalanceWhenCreatedTimestampIsNull() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(ACCOUNT_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
        long balance = 20;
        entity.setCreatedTimestamp(null);
        when(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        entity.getId(), timestamp.get(), treasuryAccountId.getId()))
                .thenReturn(Optional.of(balance));

        assertThat(accountReadableKVState.get(ACCOUNT_ID)).returns(balance, Account::tinybarBalance);

        verify(accountBalanceRepository)
                .findHistoricalAccountBalanceUpToTimestamp(entity.getId(), timestamp.get(), treasuryAccountId.getId());
    }

    @Test
    void cryptoAllowancesMatchValuesFromRepository() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        CryptoAllowance firstAllowance = new CryptoAllowance();
        firstAllowance.setSpender(123L);
        firstAllowance.setOwner(entity.getId());
        firstAllowance.setAmount(50L);

        CryptoAllowance secondAllowance = new CryptoAllowance();
        secondAllowance.setSpender(234L);
        secondAllowance.setOwner(entity.getId());
        secondAllowance.setAmount(60L);

        when(cryptoAllowanceRepository.findByOwner(anyLong()))
                .thenReturn(Arrays.asList(firstAllowance, secondAllowance));

        List<AccountCryptoAllowance> cryptoAllowances = new ArrayList<>();
        cryptoAllowances.add(
                new AccountCryptoAllowance(getAccountId(firstAllowance.getSpender()), firstAllowance.getAmount()));
        cryptoAllowances.add(
                new AccountCryptoAllowance(getAccountId(secondAllowance.getSpender()), secondAllowance.getAmount()));

        verify(cryptoAllowanceRepository, never()).findByOwner(entity.getId());

        assertThat(accountReadableKVState.get(ACCOUNT_ID))
                .satisfies(account -> assertThat(account).returns(cryptoAllowances, Account::cryptoAllowances));

        verify(cryptoAllowanceRepository).findByOwner(entity.getId());
    }

    @Test
    void cryptoAllowancesMatchValuesFromRepositoryHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(ACCOUNT_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
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

        List<AccountCryptoAllowance> cryptoAllowances = new ArrayList<>();
        cryptoAllowances.add(
                new AccountCryptoAllowance(getAccountId(firstAllowance.getSpender()), firstAllowance.getAmount()));
        cryptoAllowances.add(
                new AccountCryptoAllowance(getAccountId(secondAllowance.getSpender()), secondAllowance.getAmount()));

        verify(cryptoAllowanceRepository, never()).findByOwnerAndTimestamp(entity.getId(), timestamp.get());

        assertThat(accountReadableKVState.get(ACCOUNT_ID))
                .satisfies(account -> assertThat(account).returns(cryptoAllowances, Account::cryptoAllowances));

        verify(cryptoAllowanceRepository).findByOwnerAndTimestamp(entity.getId(), timestamp.get());
    }

    @Test
    void fungibleTokenAllowancesMatchValuesFromRepository() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
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

        List<AccountFungibleTokenAllowance> tokenAllowances = new ArrayList<>();
        tokenAllowances.add(new AccountFungibleTokenAllowance(
                new TokenID(0L, 0L, firstAllowance.getTokenId()),
                getAccountId(firstAllowance.getSpender()),
                firstAllowance.getAmount()));
        tokenAllowances.add(new AccountFungibleTokenAllowance(
                new TokenID(0L, 0L, secondAllowance.getTokenId()),
                getAccountId(secondAllowance.getSpender()),
                secondAllowance.getAmount()));

        verify(tokenAllowanceRepository, never()).findByOwner(entity.getId());

        assertThat(accountReadableKVState.get(ACCOUNT_ID))
                .satisfies(account -> assertThat(account).returns(tokenAllowances, Account::tokenAllowances));

        verify(tokenAllowanceRepository).findByOwner(entity.getId());
    }

    @Test
    void fungibleTokenAllowancesMatchValuesFromRepositoryHistorical() {
        when(contractCallContext.getTimestamp()).thenReturn(timestamp);
        when(commonEntityAccessor.get(ACCOUNT_ID, timestamp)).thenReturn(Optional.ofNullable(entity));
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

        List<AccountFungibleTokenAllowance> tokenAllowances = new ArrayList<>();
        tokenAllowances.add(new AccountFungibleTokenAllowance(
                new TokenID(0L, 0L, firstAllowance.getTokenId()),
                getAccountId(firstAllowance.getSpender()),
                firstAllowance.getAmount()));
        tokenAllowances.add(new AccountFungibleTokenAllowance(
                new TokenID(0L, 0L, secondAllowance.getTokenId()),
                getAccountId(secondAllowance.getSpender()),
                secondAllowance.getAmount()));

        verify(tokenAllowanceRepository, never()).findByOwnerAndTimestamp(entity.getId(), timestamp.get());

        assertThat(accountReadableKVState.get(ACCOUNT_ID))
                .satisfies(account -> assertThat(account).returns(tokenAllowances, Account::tokenAllowances));

        verify(tokenAllowanceRepository).findByOwnerAndTimestamp(entity.getId(), timestamp.get());
    }

    @Test
    void approveForAllNftsMatchValuesFromRepository() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
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

        List<AccountApprovalForAllAllowance> approveForAllAllowances = new ArrayList<>();
        approveForAllAllowances.add(new AccountApprovalForAllAllowance(
                new TokenID(0L, 0L, firstAllowance.getTokenId()), getAccountId(firstAllowance.getSpender())));
        approveForAllAllowances.add(new AccountApprovalForAllAllowance(
                new TokenID(0L, 0L, secondAllowance.getTokenId()), getAccountId(secondAllowance.getSpender())));

        verify(nftAllowanceRepository, never()).findByOwnerAndApprovedForAllIsTrue(entity.getId());

        assertThat(accountReadableKVState.get(ACCOUNT_ID)).satisfies(account -> assertThat(account)
                .returns(approveForAllAllowances, Account::approveForAllNftAllowances));

        verify(nftAllowanceRepository).findByOwnerAndApprovedForAllIsTrue(entity.getId());
    }

    @Test
    void numTokenAssociationsAndNumPositiveBalancesMatchValuesFromRepository() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        when(tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(anyLong()))
                .thenReturn(associationsCount);

        verify(tokenAccountRepository, never()).countByAccountIdAndAssociatedGroupedByBalanceIsPositive(entity.getId());

        assertThat(accountReadableKVState.get(ACCOUNT_ID)).satisfies(account -> assertThat(account)
                .returns(POSITIVE_BALANCES + NEGATIVE_BALANCES, Account::numberAssociations)
                .returns(POSITIVE_BALANCES, Account::numberPositiveBalances));

        verify(tokenAccountRepository, times(1))
                .countByAccountIdAndAssociatedGroupedByBalanceIsPositive(entity.getId());
    }

    @Test
    void whenAccountNumIsReadPutAliasInCache() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ACCOUNT_ID, Optional.empty())).thenReturn(Optional.ofNullable(entity));
        assertThat(contractCallContext
                        .getReadCacheState(AliasesReadableKVState.STATE_ID)
                        .containsKey(EVM_ADDRESS_BYTES))
                .isFalse();
        assertThat(accountReadableKVState.get(ACCOUNT_ID)).satisfies(account -> assertThat(account)
                .returns(ACCOUNT_ID, Account::accountId)
                .returns(EVM_ADDRESS_BYTES.value(), Account::alias));
        assertThat(contractCallContext
                        .getReadCacheState(AliasesReadableKVState.STATE_ID)
                        .containsKey(EVM_ADDRESS_BYTES))
                .isTrue();
    }

    @Test
    void sizeIsAlwaysEmpty() {
        assertThat(accountReadableKVState.size()).isZero();
    }

    @Test
    void iterateReturnsEmptyIterator() {
        assertThat(accountReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }

    @Test
    void returnsNullForNonSystemAccountWhenNotFound() {
        final var nonSystemKey = new AccountID(0, 0, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, 2000L));
        when(commonEntityAccessor.get(eq(nonSystemKey), any())).thenReturn(Optional.empty());

        assertThat(accountReadableKVState.readFromDataSource(nonSystemKey)).isNull();
    }

    @Test
    void returnsDummyAccountForSystemAccountWhenNotFound() {
        final var systemKey = new AccountID(0, 0, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, 50L));
        when(commonEntityAccessor.get(systemKey, Optional.empty())).thenReturn(Optional.empty());

        assertThat(accountReadableKVState.readFromDataSource(systemKey)).satisfies(account -> assertThat(account)
                .isNotNull()
                .returns(systemKey, Account::accountId)
                .returns(1_000_000L, Account::tinybarBalance));
    }

    private AccountID getAccountId(final Long num) {
        return new AccountID(0L, 0L, new OneOf<>(AccountOneOfType.ACCOUNT_NUM, num));
    }
}
