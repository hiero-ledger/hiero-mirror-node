// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.SystemEntity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.EntityIdUtils;
import java.util.Optional;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenBalanceRepository;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenRelationshipDatabaseAccessorTest {

    private static final Optional<Long> timestamp = Optional.of(1234L);
    private static final Address TOKEN_ADDRESS = Address.ALTBN128_ADD;
    private static final Address ACCOUNT_ADDRESS = Address.ALTBN128_MUL;
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @InjectMocks
    TokenRelationshipDatabaseAccessor tokenRelationshipDatabaseAccessor;

    @Mock
    private TokenDatabaseAccessor tokenDatabaseAccessor;

    @Mock
    private AccountDatabaseAccessor accountDatabaseAccessor;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    @Mock
    private TokenBalanceRepository tokenBalanceRepository;

    @Mock(strictness = Strictness.LENIENT)
    private SystemEntity systemEntity;

    @Mock
    private NftRepository nftRepository;

    private Account account;
    private Token token;
    private EntityId treasuryAccountId;

    @BeforeEach
    void setup() {
        treasuryAccountId = new SystemEntity(CommonProperties.getInstance()).treasuryAccount();
        account = mock(Account.class);
        when(account.getId()).thenReturn(new Id(1, 2, 3));
        token = mock(Token.class);
        when(token.getId()).thenReturn(new Id(4, 5, 6));
        when(this.systemEntity.treasuryAccount()).thenReturn(treasuryAccountId);
    }

    @Test
    void get() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.GRANTED)
                        .automaticAssociation(true))
                .get();

        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(token));
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(token.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), Optional.empty()))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(true, TokenRelationship::isFrozen)
                        .returns(true, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isDestroyed)
                        .returns(false, TokenRelationship::isNotYetPersisted)
                        .returns(true, TokenRelationship::isAutomaticAssociation)
                        .returns(0L, TokenRelationship::getBalanceChange));
    }

    @Test
    void getWhenKycNotApplicable() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                        .automaticAssociation(true))
                .get();

        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(token));
        when(token.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), Optional.empty()))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(true, TokenRelationship::isFrozen)
                        .returns(true, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isDestroyed)
                        .returns(false, TokenRelationship::isNotYetPersisted)
                        .returns(true, TokenRelationship::isAutomaticAssociation)
                        .returns(0L, TokenRelationship::getBalanceChange));
    }

    @Test
    void getWhenKycNotApplicableHistorical() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .tokenId(EntityIdUtils.entityIdFromId(token.getId()).getId())
                        .accountId(EntityIdUtils.entityIdFromId(account.getId()).getId())
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.NOT_APPLICABLE)
                        .automaticAssociation(true))
                .get();

        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, timestamp)).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, timestamp)).thenReturn(Optional.of(token));
        when(tokenAccountRepository.findByIdAndTimestamp(
                        tokenAccount.getAccountId(), tokenAccount.getTokenId(), timestamp.get()))
                .thenReturn(Optional.of(tokenAccount));
        when(token.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), timestamp))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(true, TokenRelationship::isFrozen)
                        .returns(true, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isDestroyed)
                        .returns(false, TokenRelationship::isNotYetPersisted)
                        .returns(true, TokenRelationship::isAutomaticAssociation)
                        .returns(0L, TokenRelationship::getBalanceChange));
    }

    @Test
    void getWhenKycRevoked() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .freezeStatus(TokenFreezeStatusEnum.FROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .automaticAssociation(true))
                .get();

        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(token));
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(token.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), Optional.empty()))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(true, TokenRelationship::isFrozen)
                        .returns(false, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isDestroyed)
                        .returns(false, TokenRelationship::isNotYetPersisted)
                        .returns(true, TokenRelationship::isAutomaticAssociation)
                        .returns(0L, TokenRelationship::getBalanceChange));
    }

    @Test
    void getFungibleBalanceHistorical() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .tokenId(EntityIdUtils.entityIdFromId(token.getId()).getId())
                        .accountId(EntityIdUtils.entityIdFromId(account.getId()).getId())
                        .automaticAssociation(true))
                .get();
        final long balance = 15L;
        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, timestamp)).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, timestamp)).thenReturn(Optional.of(token));
        when(tokenAccountRepository.findByIdAndTimestamp(
                        tokenAccount.getAccountId(), tokenAccount.getTokenId(), timestamp.get()))
                .thenReturn(Optional.of(tokenAccount));
        when(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenAccount.getTokenId(),
                        tokenAccount.getAccountId(),
                        timestamp.get(),
                        treasuryAccountId.getId()))
                .thenReturn(Optional.of(balance));
        when(token.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), timestamp))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(balance, TokenRelationship::getBalance));
    }

    @Test
    void getFungibleBalanceBeforeAccountCreationHistorical() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .tokenId(EntityIdUtils.entityIdFromId(token.getId()).getId())
                        .accountId(EntityIdUtils.entityIdFromId(account.getId()).getId())
                        .automaticAssociation(true))
                .get();
        final long balance = 0L;
        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, timestamp)).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, timestamp)).thenReturn(Optional.of(token));
        when(tokenAccountRepository.findByIdAndTimestamp(
                        tokenAccount.getAccountId(), tokenAccount.getTokenId(), timestamp.get()))
                .thenReturn(Optional.of(tokenAccount));
        when(token.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);
        when(account.getCreatedTimestamp()).thenReturn(timestamp.get() + 1);

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), timestamp))
                .hasValueSatisfying(tokenRelationship ->
                        assertThat(tokenRelationship).returns(balance, TokenRelationship::getBalance));
    }

    @Test
    void getNftBalanceHistorical() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .tokenId(EntityIdUtils.entityIdFromId(token.getId()).getId())
                        .accountId(EntityIdUtils.entityIdFromId(account.getId()).getId())
                        .automaticAssociation(true))
                .get();
        final long balance = 2L;
        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, timestamp)).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, timestamp)).thenReturn(Optional.of(token));
        when(tokenAccountRepository.findByIdAndTimestamp(
                        tokenAccount.getAccountId(), tokenAccount.getTokenId(), timestamp.get()))
                .thenReturn(Optional.of(tokenAccount));
        when(token.getType()).thenReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        when(nftRepository.nftBalanceByAccountIdTokenIdAndTimestamp(
                        tokenAccount.getAccountId(), tokenAccount.getTokenId(), timestamp.get()))
                .thenReturn(Optional.of(balance));

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), timestamp))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(account, TokenRelationship::getAccount)
                        .returns(token, TokenRelationship::getToken)
                        .returns(balance, TokenRelationship::getBalance));
    }

    @Test
    void getBooleansFalse() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.associated(true)
                        .freezeStatus(TokenFreezeStatusEnum.UNFROZEN)
                        .kycStatus(TokenKycStatusEnum.REVOKED)
                        .automaticAssociation(false))
                .get();

        when(accountDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(account));
        when(tokenDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(token));
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(token.getType()).thenReturn(TokenType.FUNGIBLE_COMMON);

        assertThat(tokenRelationshipDatabaseAccessor.get(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), Optional.empty()))
                .hasValueSatisfying(tokenRelationship -> assertThat(tokenRelationship)
                        .returns(false, TokenRelationship::isFrozen)
                        .returns(false, TokenRelationship::isKycGranted)
                        .returns(false, TokenRelationship::isAutomaticAssociation));
    }
}
