// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.importer.domain.EvmAddressMapping;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public interface EntityRepository extends CrudRepository<Entity, Long> {
    @Query("select id from entity where alias = :alias and deleted <> true")
    Optional<Long> findByAlias(byte[] alias);

    @Query("select id from entity where evm_address = :evmAddress and deleted <> true")
    Optional<Long> findByEvmAddress(byte[] evmAddress);

    @Query("select evm_address,id from entity where id in (:ids) and length(evm_address) > 0")
    List<EvmAddressMapping> findEvmAddressesByIds(Iterable<? extends Long> ids);

    @Modifying
    @Query("update entity set type = 'CONTRACT' where id in (:ids) and type <> 'CONTRACT'")
    int updateContractType(Iterable<Long> ids);
}
