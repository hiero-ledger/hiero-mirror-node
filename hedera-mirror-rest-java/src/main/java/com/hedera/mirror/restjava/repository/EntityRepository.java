// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.repository;

import com.hedera.mirror.common.domain.entity.Entity;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface EntityRepository extends CrudRepository<Entity, Long> {

    @Query(value = "select id from entity where alias = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findByAlias(byte[] alias);

    @Query(value = "select id from entity where evm_address = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findByEvmAddress(byte[] evmAddress);
}
