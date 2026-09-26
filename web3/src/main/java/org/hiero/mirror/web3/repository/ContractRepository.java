// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_CONTRACT;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.contract.Contract;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends CrudRepository<Contract, Long> {

    @Cacheable(cacheNames = CACHE_NAME_CONTRACT, cacheManager = CACHE_MANAGER_CONTRACT, unless = "#result == null")
    @Query(value = "select runtime_bytecode from contract where id = :contractId", nativeQuery = true)
    Optional<byte[]> findRuntimeBytecode(final Long contractId);

    @Query(value = """
                    with active_contracts as (
                        (
                            select id
                            from entity
                            where id in (:contractIds)
                              and lower(timestamp_range) <= :consensusTimestamp
                              and deleted is not true
                              and type = 'CONTRACT'
                        )
                        union all
                        (
                            select id
                            from entity_history
                            where id in (:contractIds)
                              and lower(timestamp_range) <= :consensusTimestamp
                              and deleted is not true
                              and type = 'CONTRACT'
                        )
                    )
                    select c.*
                    from contract c
                    inner join active_contracts ac on ac.id = c.id
                    where c.runtime_bytecode is not null
                    """, nativeQuery = true)
    List<Contract> findByIdsAndConsensusTimestamp(
            @Param("contractIds") final Collection<Long> contractIds,
            @Param("consensusTimestamp") long consensusTimestamp);
}
