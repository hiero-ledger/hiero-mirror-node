// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asHexedEvmAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.AbstractNft;
import org.hiero.mirror.common.domain.token.Nft;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.hiero.mirror.web3.ContextExtension;
import org.hiero.mirror.web3.evm.store.Store.OnMissing;
import org.hiero.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.CustomFeeDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.DatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.TokenDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.TokenRelationshipDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.UniqueTokenDatabaseAccessor;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.CryptoAllowanceRepository;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.NftAllowanceRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenAllowanceRepository;
import org.hiero.mirror.web3.repository.TokenBalanceRepository;
import org.hiero.mirror.web3.repository.TokenRepository;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class StoreImplTest {

    private static final DomainBuilder domainBuilder = new DomainBuilder();
    private static final EntityId uniqueTokenId = domainBuilder.entityId();
    private static final Address TOKEN_ADDRESS = Address.fromHexString(asHexedEvmAddress(uniqueTokenId.toTokenID()));
    private static final Address ACCOUNT_ADDRESS = Address.BLS12_MAP_FP2_TO_G2;
    private static final String ALIAS_HEX = "0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb";
    private static final Address ALIAS = Address.fromHexString(ALIAS_HEX);
    private static final Id TOKEN_ID = Id.fromGrpcAccount(accountIdFromEvmAddress(TOKEN_ADDRESS));
    private static final Id ACCOUNT_ID = Id.fromGrpcAccount(accountIdFromEvmAddress(ACCOUNT_ADDRESS));

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    @Mock
    private TokenBalanceRepository tokenBalanceRepository;

    @Mock
    private CustomFeeDatabaseAccessor customFeeDatabaseAccessor;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private NftAllowanceRepository nftAllowanceRepository;

    @Mock
    private TokenAllowanceRepository tokenAllowanceRepository;

    @Mock
    private CryptoAllowanceRepository cryptoAllowanceRepository;

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private Entity tokenModel;

    @Mock
    private Entity accountModel;

    @Mock
    private Token token;

    @Mock
    private TokenAccount tokenAccount;

    @Mock(strictness = Strictness.LENIENT)
    private Nft nft;

    @Mock
    private OptionValidator validator;

    private StoreImpl subject;

    @BeforeEach
    void setup() {
        final var commonProperties = new CommonProperties();
        final var systemEntity = new SystemEntity(commonProperties);
        final var accountDatabaseAccessor = new AccountDatabaseAccessor(
                entityDatabaseAccessor,
                nftAllowanceRepository,
                nftRepository,
                tokenAllowanceRepository,
                cryptoAllowanceRepository,
                tokenAccountRepository,
                accountBalanceRepository,
                systemEntity);
        final var tokenDatabaseAccessor = new TokenDatabaseAccessor(
                tokenRepository,
                entityDatabaseAccessor,
                entityRepository,
                customFeeDatabaseAccessor,
                nftRepository,
                systemEntity);
        final var tokenRelationshipDatabaseAccessor = new TokenRelationshipDatabaseAccessor(
                tokenDatabaseAccessor,
                accountDatabaseAccessor,
                tokenAccountRepository,
                tokenBalanceRepository,
                nftRepository,
                systemEntity);
        final var uniqueTokenDatabaseAccessor = new UniqueTokenDatabaseAccessor(nftRepository);
        final var entityDbAccessor = new EntityDatabaseAccessor(entityRepository);
        final List<DatabaseAccessor<Object, ?>> accessors = List.of(
                accountDatabaseAccessor,
                tokenDatabaseAccessor,
                tokenRelationshipDatabaseAccessor,
                uniqueTokenDatabaseAccessor,
                entityDbAccessor);
        final var stackedStateFrames = new StackedStateFrames(accessors);
        subject = new StoreImpl(stackedStateFrames, validator);
    }

    @Test
    void getAccountWithoutThrow() {
        when(entityDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(accountModel));
        when(accountModel.getId()).thenReturn(12L);
        when(accountModel.getNum()).thenReturn(12L);
        when(accountModel.getType()).thenReturn(EntityType.ACCOUNT);
        final var account = subject.getAccount(ACCOUNT_ADDRESS, OnMissing.DONT_THROW);
        assertThat(account.getId()).isEqualTo(new Id(0, 0, 12));
    }

    @Test
    void getAccountThrowIfMissing() {
        assertThatThrownBy(() -> subject.getAccount(ACCOUNT_ADDRESS, OnMissing.THROW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void updateAccount() {
        final var account = new Account(1L, ACCOUNT_ID, 1L);
        subject.wrap();
        subject.updateAccount(account);
        assertEquals(account, subject.getAccount(ACCOUNT_ADDRESS, OnMissing.DONT_THROW));
    }

    @Test
    void getTokenWithoutThrow() {
        when(entityDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(tokenModel));
        when(tokenModel.getId()).thenReturn(6L);
        when(tokenModel.getNum()).thenReturn(6L);
        when(tokenModel.getType()).thenReturn(EntityType.TOKEN);
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        final var retrievedToken = subject.getToken(TOKEN_ADDRESS, OnMissing.DONT_THROW);
        assertThat(retrievedToken.getId()).isEqualTo(new Id(0, 0, 6L));
    }

    @Test
    void getTokenThrowIfMissing() {
        assertThatThrownBy(() -> subject.getToken(TOKEN_ADDRESS, OnMissing.THROW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void updateToken() {
        final var retrievedToken = new com.hedera.services.store.models.Token(TOKEN_ID);
        subject.wrap();
        subject.updateToken(retrievedToken);
        assertEquals(retrievedToken, subject.getToken(TOKEN_ADDRESS, OnMissing.DONT_THROW));
    }

    @Test
    void getTokenRelationshipWithoutThrow() {
        when(entityDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(tokenModel));
        when(tokenModel.getId()).thenReturn(6L);
        when(tokenModel.getNum()).thenReturn(6L);
        when(tokenModel.getType()).thenReturn(EntityType.TOKEN);
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(entityDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(accountModel));
        when(accountModel.getId()).thenReturn(12L);
        when(accountModel.getNum()).thenReturn(12L);
        when(accountModel.getType()).thenReturn(EntityType.ACCOUNT);
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(tokenAccount.getAssociated()).thenReturn(Boolean.TRUE);
        when(token.getType()).thenReturn(TokenTypeEnum.FUNGIBLE_COMMON);
        final var tokenRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);
        assertThat(tokenRelationship.getAccount().getId()).isEqualTo(new Id(0, 0, 12));
        assertThat(tokenRelationship.getToken().getId()).isEqualTo(new Id(0, 0, 6));
    }

    @Test
    void getTokenRelationshipThrowIfMissing() {
        final var tokenRelationshipKey = new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS);
        assertThatThrownBy(() -> subject.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void updateTokenRelationship() {
        var tokenRel = new TokenRelationship(
                new com.hedera.services.store.models.Token(TOKEN_ID), new Account(0L, ACCOUNT_ID, 0L));
        subject.wrap();
        subject.updateTokenRelationship(tokenRel);
        // tokenRel is now persisted in store
        tokenRel = tokenRel.setNotYetPersisted(false);
        assertEquals(
                tokenRel,
                subject.getTokenRelationship(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW));
    }

    @Test
    void deleteTokenRelationship() {
        var tokenRelationship = new TokenRelationship(
                new com.hedera.services.store.models.Token(TOKEN_ID), new Account(0L, ACCOUNT_ID, 0L));
        subject.wrap();
        subject.updateTokenRelationship(tokenRelationship);

        final var preDeleteTokenRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);

        assertThat(preDeleteTokenRelationship.getToken().getId()).isEqualTo(TOKEN_ID);
        assertThat(preDeleteTokenRelationship.getAccount().getId()).isEqualTo(ACCOUNT_ID);

        subject.deleteTokenRelationship(preDeleteTokenRelationship);

        var postDeleteRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);

        assertThat(postDeleteRelationship.getToken().getId()).isEqualTo(Id.DEFAULT);
        assertThat(postDeleteRelationship.getAccount().getId()).isEqualTo(Id.DEFAULT);
    }

    @Test
    void deleteNotExistingTokenRelationship() {
        final var tokenRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);

        subject.wrap();
        subject.deleteTokenRelationship(tokenRelationship);

        var postDeleteRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);

        assertThat(postDeleteRelationship.getToken().getId()).isEqualTo(Id.DEFAULT);
        assertThat(postDeleteRelationship.getAccount().getId()).isEqualTo(Id.DEFAULT);
    }

    @Test
    void getUniqueTokenWithoutThrow() {
        final var nftId = new NftId(0, 0, 6, 1);
        when(nftRepository.findActiveById(6, 1)).thenReturn(Optional.of(nft));
        when(nft.getId()).thenReturn(new AbstractNft.Id(1, 6));
        when(nft.getSerialNumber()).thenReturn(1L);
        when(nft.getTokenId()).thenReturn(6L);
        final var uniqueToken = subject.getUniqueToken(nftId, OnMissing.DONT_THROW);
        assertThat(uniqueToken.getNftId()).isEqualTo(nftId);
    }

    @Test
    void getUniqueTokenThrowIfMissing() {
        final var nftId = new NftId(0, 0, 6, 1);
        assertThatThrownBy(() -> subject.getUniqueToken(nftId, OnMissing.THROW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void updateUniqueToken() {
        final var nftId = new NftId(0, 0, 0, 1);
        final var newNft = new UniqueToken(
                Id.DEFAULT,
                1,
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(accountIdFromEvmAddress(ACCOUNT_ADDRESS)),
                null,
                null);
        subject.wrap();
        subject.updateUniqueToken(newNft);
        assertEquals(newNft, subject.getUniqueToken(nftId, OnMissing.DONT_THROW));
    }

    @Test
    void loadUniqueTokensWithoutThrow() {
        final var nftId = NftId.fromGrpc(uniqueTokenId.toTokenID(), 1L);
        when(nftRepository.findActiveById(uniqueTokenId.getId(), 1)).thenReturn(Optional.of(nft));
        when(nft.getId()).thenReturn(new AbstractNft.Id(1, uniqueTokenId.getId()));
        when(nft.getSerialNumber()).thenReturn(1L);
        when(nft.getTokenId()).thenReturn(uniqueTokenId.getId());
        final var serials = List.of(nftId.serialNo());
        final var newToken = new com.hedera.services.store.models.Token(TOKEN_ID);
        final var updatedToken = subject.loadUniqueTokens(newToken, serials);
        assertThat(updatedToken.getLoadedUniqueTokens()).hasSize(serials.size());
        assertThat(updatedToken.getLoadedUniqueTokens().get(nftId.serialNo()).getNftId())
                .isEqualTo(nftId);
    }

    @Test
    void loadUniqueTokensThrowIfMissing() {
        final var nftId = new NftId(0, 0, 6, 1);
        final var serials = List.of(nftId.serialNo());
        final var newToken = new com.hedera.services.store.models.Token(TOKEN_ID);
        assertThatThrownBy(() -> subject.loadUniqueTokens(newToken, serials))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void hasApprovedForAll() {
        when(entityDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(accountModel));
        when(accountModel.getId()).thenReturn(12L);
        when(accountModel.getNum()).thenReturn(12L);
        when(accountModel.getType()).thenReturn(EntityType.ACCOUNT);
        var accountId = EntityIdUtils.accountIdFromEvmAddress(ACCOUNT_ADDRESS);
        var tokenId = EntityIdUtils.tokenIdFromEvmAddress(TOKEN_ADDRESS);
        assertThat(subject.hasApprovedForAll(Address.ZERO, accountId, tokenId)).isFalse();
        assertThat(subject.hasApprovedForAll(ACCOUNT_ADDRESS, accountId, tokenId))
                .isFalse();
    }

    @Test
    void linkAliasAccount() {
        subject.wrap();
        subject.linkAlias(ALIAS, ACCOUNT_ADDRESS);
        assertThat(subject.getAccount(ACCOUNT_ADDRESS, OnMissing.DONT_THROW)).isNotNull();
    }

    @Test
    void getHistoricalTimestamp() {
        subject.wrap();
        assertThat(subject.getHistoricalTimestamp()).isEmpty();
    }

    private void setupTokenAndAccount() {
        when(token.getType()).thenReturn(TokenTypeEnum.FUNGIBLE_COMMON);
        when(entityDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(tokenModel));
        when(tokenModel.getId()).thenReturn(uniqueTokenId.getId());
        when(tokenModel.getNum()).thenReturn(uniqueTokenId.getNum());
        when(tokenModel.getType()).thenReturn(EntityType.TOKEN);
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(entityDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(accountModel));
        when(accountModel.getId()).thenReturn(19L);
        when(accountModel.getNum()).thenReturn(19L);
        when(accountModel.getType()).thenReturn(EntityType.ACCOUNT);
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(tokenAccount.getAssociated()).thenReturn(Boolean.TRUE);
    }
}
