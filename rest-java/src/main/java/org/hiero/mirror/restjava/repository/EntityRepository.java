// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface EntityRepository extends CrudRepository<Entity, Long> {

    @Query(value = "select id from entity where alias = :alias and deleted <> true")
    Optional<Long> findByAlias(@Param("alias") byte[] alias);

    @Query(value = "select id from entity where evm_address = :evmAddress and deleted <> true")
    Optional<Long> findByEvmAddress(@Param("evmAddress") byte[] evmAddress);

    @Query(value = """
                    select cast(coalesce(sum(e.balance), 0) as bigint) as unreleased_supply,
                        cast(coalesce(max(e.balance_timestamp), 0) as bigint) as consensus_timestamp
                    from entity e
                    join unnest(
                            cast(string_to_array(:lowerBounds, ',') as bigint[]),
                            cast(string_to_array(:upperBounds, ',') as bigint[])
                         ) as ranges(min_val, max_val)
                      on e.id between ranges.min_val and ranges.max_val
                    """)
    NetworkSupply getSupply(String lowerBounds, String upperBounds);
}
