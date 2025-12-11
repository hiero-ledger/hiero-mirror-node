// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Collection;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {

    @Query(value = "select id from entity where alias = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findByAlias(byte[] alias);

    @Query(value = "select id from entity where evm_address = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findByEvmAddress(byte[] evmAddress);

    @Query(value = """
        select cast(coalesce(sum(balance), 0) as bigint) as unreleasedSupply,
               coalesce(max(balance_timestamp), 0) as consensusTimestamp
        from entity
        where id in (:unreleasedSupplyAccounts)
        """, nativeQuery = true)
    NetworkSupply getSupply(Collection<Long> unreleasedSupplyAccounts);
}
