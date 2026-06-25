// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_CONTRACT;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.web3.repository.projections.ContractBytecodeSnapshot;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends CrudRepository<Contract, Long> {

    @Cacheable(cacheNames = CACHE_NAME_CONTRACT, cacheManager = CACHE_MANAGER_CONTRACT, unless = "#result == null")
    @Query(value = "select runtime_bytecode from contract where id = :contractId", nativeQuery = true)
    Optional<byte[]> findRuntimeBytecode(final Long contractId);

    @Query(value = """
                    with combined as (
                        (
                            select id, type, lower(timestamp_range) as ts
                            from entity
                            where id in (:contractIds) and lower(timestamp_range) <= :timestamp and deleted is not true
                        )
                        union all
                        (
                            select id, type, lower(timestamp_range) as ts
                            from entity_history
                            where id in (:contractIds) and lower(timestamp_range) <= :timestamp and deleted is not true
                        )
                    ),
                    active_contracts as (
                        select distinct on (id) id
                        from combined
                        where type = 'CONTRACT'
                        order by id, ts desc
                    )
                    select c.id, c.runtime_bytecode as runtimeBytecode
                    from contract c
                    inner join active_contracts ac on ac.id = c.id
                    where c.runtime_bytecode is not null
                    """, nativeQuery = true)
    List<ContractBytecodeSnapshot> findRuntimeBytecodesByIds(
            @Param("contractIds") Collection<Long> contractIds, @Param("timestamp") long timestamp);

    @Query(value = """
                    select c.*
                    from contract c
                    inner join entity e on e.id = c.id
                    where lower(e.timestamp_range) = :consensusTimestamp
                    """, nativeQuery = true)
    List<Contract> findByConsensusTimestamp(@Param("consensusTimestamp") long consensusTimestamp);
}
