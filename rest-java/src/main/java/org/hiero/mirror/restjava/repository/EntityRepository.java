// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

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
        select
            cast(coalesce(sum(balance), 0) as bigint) as unreleasedSupply,
            cast(coalesce(max(balance_timestamp), 0) as bigint) as consensusTimestamp
        from entity
        where
            id = 2
            or id = 42
            or id between 44 and 71
            or id between 73 and 87
            or id between 99 and 100
            or id between 200 and 349
            or id between 400 and 750
        """, nativeQuery = true)
    NetworkSupply getSupply();
}
