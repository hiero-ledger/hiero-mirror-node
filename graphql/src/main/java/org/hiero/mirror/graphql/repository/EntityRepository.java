// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.graphql.data.GraphQlRepository;

@GraphQlRepository
public interface EntityRepository extends CrudRepository<Entity, Long> {
    @Query(value = "select * from entity where alias = ?1 and deleted is not true", nativeQuery = true)
    Optional<Entity> findByAlias(byte[] alias);

    @Query(value = "select * from entity where evm_address = ?1 and deleted is not true", nativeQuery = true)
    Optional<Entity> findByEvmAddress(byte[] evmAddress);
}
