// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AccountBalanceRepository extends CrudRepository<AccountBalance, AccountBalance.Id> {

    @Query(value = """
    with account_balances as (
      select distinct on (ab.account_id) ab.balance, ab.consensus_timestamp
      from account_balance ab
      join unnest(
            cast(string_to_array(:lowerBounds, ',') as bigint[]),
            cast(string_to_array(:upperBounds, ',') as bigint[])
         ) as ranges(min_val, max_val)
      on ab.account_id between ranges.min_val and ranges.max_val
      where ab.consensus_timestamp between :lowerTimestamp and :upperTimestamp
      order by ab.account_id asc, ab.consensus_timestamp desc
    )
    select cast(coalesce(sum(balance), 0) as bigint) as unreleased_supply,
           coalesce(max(consensus_timestamp), 0) as consensus_timestamp
    from account_balances
    """, nativeQuery = true)
    NetworkSupply getSupplyHistory(String lowerBounds, String upperBounds, long lowerTimestamp, long upperTimestamp);
}
