// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.importer.domain.EvmAddressMapping;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface EntityRepository extends CrudRepository<Entity, Long> {
    @Query(value = "select id from entity where alias = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findByAlias(byte[] alias);

    @Query(value = "select id from entity where evm_address = ?1 and deleted <> true", nativeQuery = true)
    Optional<Long> findByEvmAddress(byte[] evmAddress);

    @Query(value = "select evm_address,id from entity where id in (?1)", nativeQuery = true)
    List<EvmAddressMapping> findEvmAddressesByIds(Iterable<? extends Long> ids);

    @Modifying
    @Query(value = "update entity set type = 'CONTRACT' where id in (:ids) and type <> 'CONTRACT'", nativeQuery = true)
    int updateContractType(Iterable<Long> ids);
}
