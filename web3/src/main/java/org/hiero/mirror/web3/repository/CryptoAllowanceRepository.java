// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import org.hiero.mirror.common.domain.entity.AbstractCryptoAllowance;
import org.hiero.mirror.common.domain.entity.CryptoAllowance;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface CryptoAllowanceRepository extends CrudRepository<CryptoAllowance, AbstractCryptoAllowance.Id> {
    List<CryptoAllowance> findByOwner(long owner);

    /**
     * Retrieves the most recent state of the crypto allowances by their owner id up to a given block timestamp.
     * It takes into account the crypto transfers that happened up to the given block timestamp, sums them up
     * and decreases the crypto allowances' amounts with the transfers that occurred.
     *
     * @param owner the owner ID of the crypto allowance to be retrieved.
     * @param blockTimestamp the block timestamp used to filter the results.
     * @return a list containing the crypto allowances' states for the specified owner at the specified timestamp.
     *         If there is no record found for the given criteria, an empty list is returned.
     */
    @Query(value = """
                    with crypto_allowances as (
                        select *
                        from
                        (
                            select *, row_number() over (
                                partition by spender
                                order by lower(timestamp_range) desc
                            ) as row_number
                            from
                            (
                                (
                                    select *
                                    from crypto_allowance
                                    where owner = :owner
                                        and lower(timestamp_range) <= :blockTimestamp
                                )
                                union all
                                (
                                    select *
                                    from crypto_allowance_history
                                    where owner = :owner
                                        and lower(timestamp_range) <= :blockTimestamp
                                )
                            ) as all_crypto_allowances
                        ) as grouped_crypto_allowances
                        where row_number = 1 and amount_granted > 0
                        ), transfers as (
                        select spender, sum(ct.amount) as amount
                        from crypto_transfer ct
                            join crypto_allowances ca
                            on ct.entity_id = ca.owner
                                and ct.payer_account_id = ca.spender
                        where is_approval is true
                            and consensus_timestamp <= :blockTimestamp
                            and consensus_timestamp > lower(ca.timestamp_range)
                        group by spender
                        )
                    select *
                    from (
                        select amount_granted, owner, payer_account_id, spender, timestamp_range, amount_granted + coalesce((select amount from transfers tr where tr.spender = ca.spender), 0) as amount
                        from crypto_allowances ca
                    ) result
                    where amount > 0
                    """, nativeQuery = true)
    List<CryptoAllowance> findByOwnerAndTimestamp(long owner, long blockTimestamp);
}
