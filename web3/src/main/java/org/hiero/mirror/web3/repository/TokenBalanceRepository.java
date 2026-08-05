// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenBalanceRepository extends CrudRepository<TokenBalance, TokenBalance.Id> {

    /**
     * Retrieves the token balance of a specific token for a specific account at the last consensus timestamp
     * before оr equal to the provided block timestamp.
     *
     * @param tokenId         the ID of the token whose balance is to be retrieved.
     * @param accountId       the ID of the account whose balance is to be retrieved.
     * @param blockTimestamp  the block timestamp used as the upper limit to retrieve the token balance.
     *                        The method will retrieve the last token balance before or equal to this timestamp.
     * @return an Optional containing the balance if found, or an empty Optional if no matching balance
     *         entry is found before the given block timestamp.
     */
    @Query(value = """
                select * from token_balance
                where
                    token_id = :tokenId and
                    account_id = :accountId and
                    consensus_timestamp <= :blockTimestamp
                order by consensus_timestamp desc
                limit 1
                """)
    Optional<TokenBalance> findByIdAndTimestampLessThan(long tokenId, long accountId, long blockTimestamp);

    /**
     * Finds the historical token balance for a given token ID and account ID combination based on a specific block timestamp.
     * This method calculates the historical balance by summing the token transfers and adding the sum to the initial balance
     * found at a timestamp less than the given block timestamp. If no token_balance is found for the given token_id,
     * account_id, and consensus timestamp, a balance of 0 will be returned.
     *
     * For more information please refer to `AccountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp`
     *
     * @param tokenId         the ID of the token.
     * @param accountId       the ID of the account.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the historical balance at the specified timestamp.
     *         If there are no token transfers between the consensus_timestamp of token_balance and the block timestamp,
     *         the method will return the balance present at consensus_timestamp.
     */
    @Query(value = """
                    with balance_timestamp as (
                    select consensus_timestamp
                    from account_balance
                    where account_id = :treasuryAccountId and
                        consensus_timestamp > :blockTimestamp - 2678400000000000 and consensus_timestamp <= :blockTimestamp
                    order by consensus_timestamp desc
                    limit 1
                    ), base as (
                        select tb.balance
                        from token_balance as tb, balance_timestamp as bt
                        where
                            token_id = :tokenId and
                            account_id = :accountId and
                            tb.consensus_timestamp > bt.consensus_timestamp - 2678400000000000 and
                            tb.consensus_timestamp <= bt.consensus_timestamp
                        order by tb.consensus_timestamp desc
                        limit 1
                    ), change as (
                        select sum(amount) as amount
                        from token_transfer as tt
                        where
                            token_id = :tokenId and
                            account_id = :accountId and
                            tt.consensus_timestamp > coalesce((select consensus_timestamp from balance_timestamp), 0) and
                            tt.consensus_timestamp <= :blockTimestamp
                    )
                    select coalesce((select balance from base), 0) + coalesce((select amount from change), 0)
                    """)
    Optional<Long> findHistoricalTokenBalanceUpToTimestamp(
            long tokenId, long accountId, long blockTimestamp, long treasuryAccountId);
}
