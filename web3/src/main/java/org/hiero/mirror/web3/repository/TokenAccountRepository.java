// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_TOKEN;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN_ACCOUNT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN_ACCOUNT_COUNT;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, AbstractTokenAccount.Id> {

    @Override
    @Cacheable(cacheNames = CACHE_NAME_TOKEN_ACCOUNT, cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    @Query(value = """
                    select
                      ta.account_id,
                      ta.associated,
                      ta.automatic_association,
                      ta.balance,
                      ta.balance_timestamp,
                      ta.created_timestamp,
                      coalesce(ta.freeze_status, t.freeze_status) as freeze_status,
                      coalesce(ta.kyc_status, t.kyc_status) as kyc_status,
                      ta.timestamp_range,
                      ta.token_id
                    from token_account as ta
                    left join (select * from token where token_id = :#{#id.tokenId}) as t on true
                    where ta.account_id = :#{#id.accountId} and ta.token_id = :#{#id.tokenId}
                    """, nativeQuery = true)
    Optional<TokenAccount> findById(@Param("id") AbstractTokenAccount.Id id);

    @Cacheable(cacheNames = CACHE_NAME_TOKEN_ACCOUNT_COUNT, cacheManager = CACHE_MANAGER_TOKEN)
    @Query(
            value = "select count(*) as tokenCount, balance>0 as isPositiveBalance from token_account "
                    + "where account_id = ?1 and associated is true group by balance>0",
            nativeQuery = true)
    List<TokenAccountAssociationsCount> countByAccountIdAndAssociatedGroupedByBalanceIsPositive(long accountId);

    /**
     * Retrieves the most recent state of number of associated tokens (and if their balance is positive)
     * by accountId up to a given block timestamp.
     * The method considers both the current state of the token account and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     *
     * @param accountId the ID of the account
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return List of {@link TokenAccountAssociationsCount}
     */
    @Query(value = """
                    select count(*) as tokenCount, balance>0 as isPositiveBalance
                    from (
                        (
                            select *
                            from token_account
                            where account_id = :accountId
                                and associated is true
                                and lower(timestamp_range) <= :blockTimestamp
                        )
                        union all
                        (
                            select *
                            from token_account_history
                            where account_id = :accountId
                                and associated is true
                                and timestamp_range @> :blockTimestamp
                        )
                    ) as ta
                    group by balance>0
                    """, nativeQuery = true)
    List<TokenAccountAssociationsCount> countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
            long accountId, long blockTimestamp);

    /**
     * Retrieves the most recent state of a token account by its ID up to a given block timestamp.
     * The method considers both the current state of the token account and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     *
     * @param accountId the ID of the account
     * @param tokenId the ID of the token
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the token account's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(value = """
                    select
                      ta.account_id,
                      ta.associated,
                      ta.automatic_association,
                      ta.balance,
                      ta.balance_timestamp,
                      ta.created_timestamp,
                      coalesce(ta.freeze_status, t.freeze_status) as freeze_status,
                      coalesce(ta.kyc_status, t.kyc_status) as kyc_status,
                      ta.timestamp_range,
                      ta.token_id
                    from (
                            (
                        select *
                        from token_account
                        where account_id = :accountId
                            and token_id = :tokenId
                            and lower(timestamp_range) <= :blockTimestamp
                            )
                            union all
                            (
                        select *
                        from token_account_history
                        where account_id = :accountId
                            and token_id = :tokenId
                            and lower(timestamp_range) <= :blockTimestamp
                        order by lower(timestamp_range) desc
                        limit 1
                            )
                            order by timestamp_range desc
                            limit 1
                    ) as ta
                    left join (select * from token where token_id = :tokenId) as t on true;
                    """, nativeQuery = true)
    Optional<TokenAccount> findByIdAndTimestamp(long accountId, long tokenId, long blockTimestamp);
}
