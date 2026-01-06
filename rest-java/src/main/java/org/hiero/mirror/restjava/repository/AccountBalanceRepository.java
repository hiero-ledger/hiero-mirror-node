// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AccountBalanceRepository extends CrudRepository<AccountBalance, AccountBalance.Id> {

    @Query(value = """
    with account_balances as (
        select distinct on (account_id) balance, consensus_timestamp
        from account_balance
        where consensus_timestamp between :lowerTimestamp and :upperTimestamp
          and (
                account_id = 2
             or account_id = 42
             or account_id between 44 and 71
             or account_id between 73 and 87
             or account_id between 99 and 100
             or account_id between 200 and 349
             or account_id between 400 and 750
          )
        order by account_id asc, consensus_timestamp desc
    )
    select cast(coalesce(sum(balance), 0) as bigint) as unreleasedSupply,
           coalesce(max(consensus_timestamp), 0) as consensusTimestamp
    from account_balances
    """, nativeQuery = true)
    NetworkSupply getSupplyHistory(long lowerTimestamp, long upperTimestamp);
}
