// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.token;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.SystemEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.CustomFeeDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.TokenDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.TokenRelationshipDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.UniqueTokenDatabaseAccessor;
import com.hedera.mirror.web3.repository.AccountBalanceRepository;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.CustomFeeRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.mirror.web3.repository.TokenBalanceRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenKeyType;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class TokenAccessorImplTest {
    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    private static final EntityId TOKEN_ENTITY_ID =
            EntityId.of(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), 1252);
    private static final Address TOKEN_ADDRESS = toAddress(TOKEN_ENTITY_ID);
    private static final EntityId ACCOUNT_ENTITY_ID =
            EntityId.of(COMMON_PROPERTIES.getShard(), COMMON_PROPERTIES.getRealm(), 1253);
    private static final Address ACCOUNT_ADDRESS = toAddress(ACCOUNT_ENTITY_ID);
    private final long serialNo = 0L;
    private final DomainBuilder domainBuilder = new DomainBuilder();
    public TokenAccessorImpl tokenAccessor;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private CustomFeeRepository customFeeRepository;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    @Mock
    private TokenBalanceRepository tokenBalanceRepository;

    @Mock
    private CryptoAllowanceRepository cryptoAllowanceRepository;

    @Mock
    private TokenAllowanceRepository tokenAllowanceRepository;

    @Mock
    private NftAllowanceRepository nftAllowanceRepository;

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MirrorNodeEvmProperties properties;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Entity entity;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Token token;

    @Mock
    private OptionValidator validator;

    private List<DatabaseAccessor<Object, ?>> accessors;
    private Store store;

    @BeforeEach
    void setUp() {
        final var entityAccessor = new EntityDatabaseAccessor(entityRepository, COMMON_PROPERTIES);
        final var customFeeAccessor = new CustomFeeDatabaseAccessor(customFeeRepository, entityAccessor);
        final var systemEntity = new SystemEntity(COMMON_PROPERTIES);
        final var tokenDatabaseAccessor = new TokenDatabaseAccessor(
                tokenRepository, entityAccessor, entityRepository, customFeeAccessor, nftRepository, systemEntity);
        final var accountDatabaseAccessor = new AccountDatabaseAccessor(
                entityAccessor,
                nftAllowanceRepository,
                nftRepository,
                tokenAllowanceRepository,
                cryptoAllowanceRepository,
                tokenAccountRepository,
                accountBalanceRepository,
                systemEntity);
        accessors = List.of(
                entityAccessor,
                customFeeAccessor,
                accountDatabaseAccessor,
                tokenDatabaseAccessor,
                new TokenRelationshipDatabaseAccessor(
                        tokenDatabaseAccessor,
                        accountDatabaseAccessor,
                        tokenAccountRepository,
                        tokenBalanceRepository,
                        nftRepository,
                        systemEntity),
                new UniqueTokenDatabaseAccessor(nftRepository));
        final var stackedStateFrames = new StackedStateFrames(accessors);
        store = new StoreImpl(stackedStateFrames, validator);
        tokenAccessor = new TokenAccessorImpl(properties, store, mirrorEvmContractAliases);
    }

    @Test
    void evmNftInfo() {
        int createdTimestampNanos = 13;
        long createdTimestampSecs = 12;
        Nft nft = domainBuilder
                .nft()
                .customize(n -> n.createdTimestamp(createdTimestampSecs * 1_000_000_000 + createdTimestampNanos))
                .get();
        when(nftRepository.findActiveById(TOKEN_ENTITY_ID.getId(), serialNo)).thenReturn(Optional.of(nft));

        final var expected = new EvmNftInfo(
                serialNo, toAddress(nft.getAccountId()), createdTimestampSecs, nft.getMetadata(), Address.ZERO, null);
        final var result = tokenAccessor.evmNftInfo(TOKEN_ADDRESS, serialNo);
        assertThat(result).isNotEmpty();
        assertEquals(expected.getSerialNumber(), result.get().getSerialNumber());
        assertEquals(expected.getSpender(), result.get().getSpender());
        assertEquals(expected.getAccount(), result.get().getAccount());
    }

    @Test
    void evmNftInfoWithNoOwnerAndNoSpender() {
        int createdTimestampNanos = 13;
        long createdTimestampSecs = 12;
        Nft nft = domainBuilder
                .nft()
                .customize(n -> {
                    n.createdTimestamp(createdTimestampSecs * 1_000_000_000 + createdTimestampNanos);
                    n.spender(EntityId.EMPTY);
                    n.accountId(EntityId.EMPTY);
                })
                .get();
        when(nftRepository.findActiveById(TOKEN_ENTITY_ID.getId(), serialNo)).thenReturn(Optional.of(nft));

        final var expected =
                new EvmNftInfo(serialNo, Address.ZERO, createdTimestampSecs, nft.getMetadata(), Address.ZERO, null);
        final var result = tokenAccessor.evmNftInfo(TOKEN_ADDRESS, serialNo);
        assertThat(result).isNotEmpty();
        assertEquals(expected.getSerialNumber(), result.get().getSerialNumber());
        assertEquals(expected.getSpender(), result.get().getSpender());
        assertEquals(expected.getAccount(), result.get().getAccount());
    }

    @Test
    void isTokenAddress() {
        when(entityRepository.findByIdAndDeletedIsFalse(TOKEN_ENTITY_ID.getId()))
                .thenReturn(Optional.of(entity));
        when(entity.getId()).thenReturn(0L);
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(tokenRepository.findById(0L)).thenReturn(Optional.of(token));
        when(token.getType()).thenReturn(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        assertTrue(tokenAccessor.isTokenAddress(TOKEN_ADDRESS));
    }

    @Test
    void isFrozen() {
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.FROZEN);
        tokenAccount.setAssociated(true);
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(token.getType()).thenReturn(TokenTypeEnum.FUNGIBLE_COMMON);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.ACCOUNT, EntityType.TOKEN);
        assertTrue(tokenAccessor.isFrozen(ACCOUNT_ADDRESS, TOKEN_ADDRESS));
    }

    @Test
    void isKyc() {
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setKycStatus(TokenKycStatusEnum.GRANTED);
        tokenAccount.setAssociated(true);
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(token.getType()).thenReturn(TokenTypeEnum.FUNGIBLE_COMMON);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.ACCOUNT, EntityType.TOKEN);
        assertTrue(tokenAccessor.isKyc(ACCOUNT_ADDRESS, TOKEN_ADDRESS));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void infoForTokenCustomFees() {
        final var customFee = new CustomFee();
        final EntityId collectorId = EntityId.of(1L, 2L, 3L);
        customFee.addFixedFee(FixedFee.builder().collectorAccountId(collectorId).build());
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(collectorId.toEntity()));
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(token.getType()).thenReturn(TokenTypeEnum.FUNGIBLE_COMMON);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(customFeeRepository.findById(any())).thenReturn(Optional.of(customFee));
        assertThat(tokenAccessor.infoForTokenCustomFees(TOKEN_ADDRESS)).isNotEmpty();
        assertEquals(
                toAddress(collectorId),
                tokenAccessor
                        .infoForTokenCustomFees(TOKEN_ADDRESS)
                        .get()
                        .get(0)
                        .getFixedFee()
                        .getFeeCollector());
    }

    @Test
    void keyOf() {
        final byte[] bytes = new byte[33];
        bytes[0] = 0x02;
        final Key key =
                Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(bytes)).build();
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(entity));
        when(entity.getKey()).thenReturn(key.toByteArray());
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(token.getWipeKey()).thenReturn(key.toByteArray());
        when(token.getType()).thenReturn(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        final var result = tokenAccessor.keyOf(TOKEN_ADDRESS, TokenKeyType.WIPE_KEY);
        assertThat(result).isNotNull();
        assertArrayEquals(key.getECDSASecp256K1().toByteArray(), result.getECDSASecp256K1());
    }

    @Test
    void symbolOfWithMissingToken() {
        when(tokenRepository.findById(any())).thenReturn(Optional.empty());
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(token.getType()).thenReturn(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        final var result = tokenAccessor.symbolOf(TOKEN_ADDRESS);
        assertEquals("", result);
    }

    @Test
    void symbolOf() {
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(token.getType()).thenReturn(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        when(token.getSymbol()).thenReturn("symbol");
        final var result = tokenAccessor.symbolOf(TOKEN_ADDRESS);
        assertEquals("symbol", result);
    }

    @Test
    void nameOfWithMissingToken() {
        when(tokenRepository.findById(any())).thenReturn(Optional.empty());
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(token.getType()).thenReturn(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        final var result = tokenAccessor.nameOf(TOKEN_ADDRESS);
        assertEquals("", result);
    }

    @Test
    void nameOf() {
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(entityRepository.findByIdAndDeletedIsFalse(any())).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(token.getType()).thenReturn(TokenTypeEnum.NON_FUNGIBLE_UNIQUE);
        when(token.getSupplyType()).thenReturn(TokenSupplyTypeEnum.FINITE);
        when(token.getName()).thenReturn("name");
        final var result = tokenAccessor.nameOf(TOKEN_ADDRESS);
        assertEquals("name", result);
    }
}
