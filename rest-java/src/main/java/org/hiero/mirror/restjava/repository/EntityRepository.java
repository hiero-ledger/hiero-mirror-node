// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.restjava.dto.NetworkSupplyProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {

    @Query(value = "select id from entity where alias = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findByAlias(byte[] alias);

    @Query(value = "select id from entity where evm_address = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findByEvmAddress(byte[] evmAddress);

    @Query(
            value =
                    """
        select coalesce(sum(balance), 0) as unreleasedSupply,
               coalesce(max(balance_timestamp), 0) as consensusTimestamp
        from entity
        where id in (:unreleasedSupplyAccounts)
        """,
            nativeQuery = true)
    NetworkSupplyProjection getSupply(List<Long> unreleasedSupplyAccounts);

    @Query(
            value =
                    """
        with account_balances as (
          select distinct on (account_id) balance, consensus_timestamp
          from account_balance
          where consensus_timestamp between :lowerTimestamp and :upperTimestamp
            and account_id in (:unreleasedSupplyAccounts)
          order by account_id asc, consensus_timestamp desc
        )
        select coalesce(sum(balance), 0) as unreleasedSupply,
               coalesce(max(consensus_timestamp), 0) as consensusTimestamp
        from account_balances
        """,
            nativeQuery = true)
    NetworkSupplyProjection getSupplyHistory(
            List<Long> unreleasedSupplyAccounts, long lowerTimestamp, long upperTimestamp);
}
