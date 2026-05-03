// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_TOKEN;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_TOKEN_TYPE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_TOKEN;

import java.util.Optional;
import org.hiero.mirror.common.domain.token.Token;
import org.hiero.mirror.common.domain.token.TokenTypeEnum;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenRepository extends CrudRepository<Token, Long> {

    @Override
    @Cacheable(cacheNames = CACHE_NAME_TOKEN, cacheManager = CACHE_MANAGER_TOKEN, unless = "#result == null")
    Optional<Token> findById(Long tokenId);

    @Cacheable(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_TOKEN_TYPE, unless = "#result == null")
    @Query(value = """
            select t.type
            from token t
            where token_id = ?1
            """, nativeQuery = true)
    Optional<TokenTypeEnum> findTypeByTokenId(Long tokenId);

    /**
     * Retrieves the most recent state of a token by its ID up to a given block timestamp.
     * The method considers both the current state of the token and its historical states
     * and returns the one that was valid just before or equal to the provided block timestamp.
     * It performs a UNION operation between the 'token' and 'token_history' tables,
     * filters the combined result set to get the records with a timestamp range
     * less than or equal to the provided block timestamp and then returns the most recent record.
     *
     * @param tokenId         the ID of the token to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the token's state at the specified timestamp.
     *         If there is no record found for the given criteria, an empty Optional is returned.
     */
    @Query(value = """
                    (
                        select *
                        from token
                        where token_id = ?1 and lower(timestamp_range) <= ?2
                    )
                    union all
                    (
                        select *
                        from token_history
                        where token_id = ?1 and lower(timestamp_range) <= ?2
                        order by lower(timestamp_range) desc
                        limit 1
                    )
                    order by timestamp_range desc
                    limit 1
                    """, nativeQuery = true)
    Optional<Token> findByTokenIdAndTimestamp(long tokenId, long blockTimestamp);

    /**
     * Finds the historical token total supply for a given token ID based on a specific block timestamp.
     * This method calculates the historical supply by summing the token transfers for burn, mint and wipe operations
     * and subtracts this amount from the historical total supply from 'token' and 'token_history' tables
     *
     * @param tokenId         the ID of the token to be retrieved.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return the token's total supply at the specified timestamp.
     * */
    @Query(value = """
                    with snapshot_timestamp as (
                      select consensus_timestamp
                      from account_balance
                      where account_id = ?3 and
                        consensus_timestamp <= ?2 and
                        consensus_timestamp > ?2 - 2678400000000000
                      order by consensus_timestamp desc
                      limit 1
                    ), snapshot as (
                      select distinct on (account_id) balance
                      from token_balance
                      where token_id = ?1 and
                        consensus_timestamp <= (select consensus_timestamp from snapshot_timestamp) and
                        consensus_timestamp <= ?2 and
                        consensus_timestamp > ?2 - 2678400000000000
                      order by account_id, consensus_timestamp desc
                    ), change as (
                      select amount
                      from token_transfer
                      where token_id = ?1 and
                        consensus_timestamp >= (select consensus_timestamp from snapshot_timestamp) and
                        consensus_timestamp <= ?2 and
                        consensus_timestamp > ?2 - 2678400000000000
                    )
                    select coalesce((select sum(balance) from snapshot), 0) + coalesce((select sum(amount) from change), 0)
                    """, nativeQuery = true)
    long findFungibleTotalSupplyByTokenIdAndTimestamp(long tokenId, long blockTimestamp, long treasuryAccountId);
}
