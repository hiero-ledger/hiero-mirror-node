// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import com.hedera.mirror.common.domain.entity.Entity;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface EntityRepository extends CrudRepository<Entity, Long> {

    @Query(
            value = "select id from entity where shard = ?1 and realm = ?2 and alias = ?3 and deleted <> true",
            nativeQuery = true)
    Optional<Long> findByAlias(long shard, long realm, byte[] alias);

    @Query(
            value = "select id from entity where shard = ?1 and realm = ?2 and evm_address = ?3 and deleted <> true",
            nativeQuery = true)
    Optional<Long> findByEvmAddress(long shard, long realm, byte[] evmAddress);

    @Modifying
    @Query(value = "update entity set type = 'CONTRACT' where id in (:ids) and type <> 'CONTRACT'", nativeQuery = true)
    int updateContractType(Iterable<Long> ids);
}
