// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.dto.NetworkSupply;

@Named
@RequiredArgsConstructor
final class AccountBalanceRepositoryCustomImpl implements AccountBalanceRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public NetworkSupply getSupplyHistory(String whereClause, long lowerTimestamp, long upperTimestamp) {
        final var queryString = String.format(
                """
        with account_balances as (
          select distinct on (account_id) balance, consensus_timestamp
          from account_balance
          where consensus_timestamp between :lowerTimestamp and :upperTimestamp
            and (%s)
          order by account_id asc, consensus_timestamp desc
        )
        select cast(coalesce(sum(balance), 0) as bigint) as unreleasedSupply,
               coalesce(max(consensus_timestamp), 0) as consensusTimestamp
        from account_balances
        """,
                whereClause);
        final var query = entityManager.createNativeQuery(queryString);
        query.setParameter("lowerTimestamp", lowerTimestamp);
        query.setParameter("upperTimestamp", upperTimestamp);
        final var result = (Object[]) query.getSingleResult();
        return new NetworkSupply(((Number) result[0]).longValue(), ((Number) result[1]).longValue());
    }
}
