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
                    select c.*
                    from contract c
                    inner join entity e on e.id = c.id
                    where lower(e.timestamp_range) <= :consensusTimestamp
                    and c.id in :contractIds
                    """, nativeQuery = true)
    List<Contract> findByIdsAndConsensusTimestamp(
            @Param("contractIds") final Collection<Long> contractIds,
            @Param("consensusTimestamp") long consensusTimestamp);
}
