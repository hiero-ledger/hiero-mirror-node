// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import org.hiero.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenBalanceRepository;
import org.hiero.mirror.web3.utils.Suppliers;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.NonNull;

@Named
@RequiredArgsConstructor
public class TokenRelationshipDatabaseAccessor extends DatabaseAccessor<Object, TokenRelationship> {

    static final Optional<Long> ZERO_BALANCE = Optional.of(0L);

    private final TokenDatabaseAccessor tokenDatabaseAccessor;
    private final AccountDatabaseAccessor accountDatabaseAccessor;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final NftRepository nftRepository;
    private final SystemEntity systemEntity;

    @Override
    public @NonNull Optional<TokenRelationship> get(@NonNull Object key, final Optional<Long> timestamp) {
        if (key instanceof TokenRelationshipKey tokenRelationshipKey) {
            return findAccount(tokenRelationshipKey.accountAddress(), timestamp)
                    .flatMap(account -> findToken(tokenRelationshipKey.tokenAddress(), timestamp)
                            .flatMap(token -> findTokenAccount(token, account, timestamp)
                                    .filter(AbstractTokenAccount::getAssociated)
                                    .map(tokenAccount -> new TokenRelationship(
                                            token,
                                            account,
                                            getBalance(account, token, tokenAccount, timestamp),
                                            TokenFreezeStatusEnum.FROZEN == tokenAccount.getFreezeStatus(),
                                            TokenKycStatusEnum.REVOKED != tokenAccount.getKycStatus(),
                                            false,
                                            false,
                                            Boolean.TRUE == tokenAccount.getAutomaticAssociation(),
                                            0))));
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(TokenRelationship.class.getTypeName(), key.getClass().getTypeName()));
    }

    /**
     * Determines fungible or NFT balance based on block context.
     */
    private Supplier<Long> getBalance(
            final Account account, final Token token, final TokenAccount tokenAccount, final Optional<Long> timestamp) {
        if (token.getType().equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
            return Suppliers.memoize(() -> getNftBalance(tokenAccount, timestamp, account.getCreatedTimestamp()));
        }
        return Suppliers.memoize(() -> getFungibleBalance(tokenAccount, timestamp, account.getCreatedTimestamp()));
    }

    /**
     * NFT Balance Explanation:
     * Non-historical Call:
     * The balance is obtained from `tokenAccount.getBalance()`.
     * Historical Call:
     * In historical block queries, as the `token_account` and `token_balance` tables lack historical state for NFT balances,
     * the NFT balance is retrieved from `NftRepository.nftBalanceByAccountIdTokenIdAndTimestamp`
     */
    private Long getNftBalance(
            final TokenAccount tokenAccount, final Optional<Long> timestamp, long accountCreatedTimestamp) {
        return timestamp
                .map(t -> {
                    if (t >= accountCreatedTimestamp) {
                        return nftRepository.nftBalanceByAccountIdTokenIdAndTimestamp(
                                tokenAccount.getAccountId(), tokenAccount.getTokenId(), t);
                    } else {
                        return ZERO_BALANCE;
                    }
                })
                .orElseGet(() -> Optional.of(tokenAccount.getBalance()))
                .orElse(0L);
    }

    /**
     * Fungible Token Balance Explanation:
     * Non-historical Call:
     * The balance is obtained from `tokenAccount.getBalance()`.
     * Historical Call:
     * In historical block queries, since the `token_account` table lacks historical state for fungible balances,
     * the fungible balance is determined from the `token_balance` table using the `findHistoricalTokenBalanceUpToTimestamp` query.
     * If the entity creation is after the passed timestamp - return 0L (the entity was not created)
     */
    private Long getFungibleBalance(
            final TokenAccount tokenAccount, final Optional<Long> timestamp, long accountCreatedTimestamp) {
        return timestamp
                .map(t -> {
                    if (t >= accountCreatedTimestamp) {
                        long treasuryAccountId = systemEntity.treasuryAccount().getId();
                        return tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                                tokenAccount.getTokenId(), tokenAccount.getAccountId(), t, treasuryAccountId);
                    } else {
                        return ZERO_BALANCE;
                    }
                })
                .orElseGet(() -> Optional.of(tokenAccount.getBalance()))
                .orElse(0L);
    }

    private Optional<Account> findAccount(Address address, final Optional<Long> timestamp) {
        return accountDatabaseAccessor.get(address, timestamp);
    }

    private Optional<Token> findToken(Address address, final Optional<Long> timestamp) {
        return tokenDatabaseAccessor.get(address, timestamp);
    }

    private Optional<TokenAccount> findTokenAccount(Token token, Account account, final Optional<Long> timestamp) {
        AbstractTokenAccount.Id id = new AbstractTokenAccount.Id();
        id.setTokenId(EntityIdUtils.entityIdFromId(token.getId()).getId());
        id.setAccountId(EntityIdUtils.entityIdFromId(account.getId()).getId());
        return timestamp
                .map(t -> tokenAccountRepository.findByIdAndTimestamp(id.getAccountId(), id.getTokenId(), t))
                .orElseGet(() -> tokenAccountRepository.findById(id));
    }
}
