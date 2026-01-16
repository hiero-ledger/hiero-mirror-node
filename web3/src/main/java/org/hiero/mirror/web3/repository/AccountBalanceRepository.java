// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AccountBalanceRepository extends CrudRepository<AccountBalance, AccountBalance.Id> {

    /**
     * Retrieves the account balance of a specific account at the last consensus timestamp
     * before оr equal to the provided block timestamp.
     *
     * @param accountId       the ID of the account whose balance is to be retrieved.
     * @param blockTimestamp  the block timestamp used as the upper limit to retrieve the account balance.
     *                        The method will retrieve the last account balance before or equal to this timestamp.
     * @return an Optional containing the balance if found, or an empty Optional if no matching balance
     *         entry is found before the given block timestamp.
     */
    @Query(value = """
                 select * from account_balance
                 where
                     account_id = ?1 and
                     consensus_timestamp <= ?2
                 order by consensus_timestamp desc
                 limit 1
                 """, nativeQuery = true)
    Optional<AccountBalance> findByIdAndTimestampLessThan(long accountId, long blockTimestamp);

    /**
     * Finds the historical account balance for a given account ID based on a specific block timestamp.
     * This method calculates the historical balance by summing the crypto transfers and adding the sum to the initial balance
     * found at a timestamp less than the given block timestamp. If no account_balance is found for the given account_id
     * and consensus timestamp, a balance of 0 will be returned.
     *
     * FYI:
     * The database insertion operates on a periodic cycle where, every X minutes, Y number of account_balance entries are dumped for
     * accounts involved in crypto_transfers. This cycle includes entries for those accounts, and it always includes an
     * entry for treasury account 0.0.2. The timestamp of 0.0.2 marks the beginning of a new cycle and the end of the
     * previous one.
     *
     * The algorithm used in this method involves the following steps:
     * 1. Find the latest balance snapshot timestamp of treasury account 0.0.2 at or before blockTimestamp. This works because
     *    the design ensures that treasury account's balance info is never deduplicated, and there will be a row for the
     *    account in every snapshot. Let's call this timestamp balanceSnapshotTimestamp.
     * 2. Find the latest balance of the specified accountId in the range (balanceSnapshotTimestamp - 31 days, balanceSnapshotTimestamp].
     * 3. Sum the crypto transfers that occurred between the balance snapshot timestamp and the given block timestamp for the
     *    specified accountId. Exclude transfers with errata 'DELETE'.
     * 4. Calculate the historical balance by adding the balance found at step 2 to the sum calculated at step 3.
     *
     * @param accountId       the ID of the account.
     * @param blockTimestamp  the block timestamp used to filter the results.
     * @return an Optional containing the historical balance at the specified timestamp.
     *         If there are no crypto transfers between the consensus_timestamp of account_balance and the block timestamp,
     *         the method will return the balance present at consensus_timestamp.
     */
    @Query(value = """
                    with balance_timestamp as (
                        select consensus_timestamp
                        from account_balance
                        where account_id = ?3 and
                            consensus_timestamp > ?2 - 2678400000000000 and
                            consensus_timestamp <= ?2
                        order by consensus_timestamp desc
                        limit 1
                    ), balance_snapshot as (
                        select ab.balance, ab.consensus_timestamp
                        from account_balance as ab, balance_timestamp as bt
                        where account_id = ?1 and
                            ab.consensus_timestamp > bt.consensus_timestamp - 2678400000000000 and
                            ab.consensus_timestamp <= bt.consensus_timestamp
                        order by ab.consensus_timestamp desc
                        limit 1
                    ), change as (
                        select sum(amount) as amount
                        from crypto_transfer as ct
                        where ct.entity_id = ?1 and
                            ct.consensus_timestamp > coalesce((select consensus_timestamp from balance_snapshot), 0) and
                            ct.consensus_timestamp <= ?2 and
                        (ct.errata is null or ct.errata <> 'DELETE')
                    )
                    select coalesce((select balance from balance_snapshot), 0) + coalesce((select amount from change), 0)
                    """, nativeQuery = true)
    Optional<Long> findHistoricalAccountBalanceUpToTimestamp(
            long accountId, long blockTimestamp, long treasuryAccountId);
}
