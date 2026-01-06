// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
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
            and account_id in (:unreleasedSupplyAccounts)
          order by account_id asc, consensus_timestamp desc
        )
        select cast(coalesce(sum(balance), 0) as bigint) as unreleasedSupply,
               coalesce(max(consensus_timestamp), 0) as consensusTimestamp
        from account_balances
        """, nativeQuery = true)
    NetworkSupply getSupplyHistory(Collection<Long> unreleasedSupplyAccounts, long lowerTimestamp, long upperTimestamp);
}
