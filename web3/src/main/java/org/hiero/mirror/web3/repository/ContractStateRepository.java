// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ContractStateRepository extends CrudRepository<ContractState, Long> {

    @Query(value = "select value from contract_state where contract_id = ?1 and slot =?2", nativeQuery = true)
    @Cacheable(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_CONTRACT_STATE)
    Optional<byte[]> findStorage(final Long contractId, final byte[] key);

    @Query(value = """
                    select slot, value from contract_state
                    where contract_id = :contractId
                    and slot in (:slots)
                    """, nativeQuery = true)
    List<ContractSlotValue> findStorageBatch(@Param("contractId") Long contractId, @Param("slots") List<byte[]> slots);

    /**
     * This method retrieves the most recent contract state storage value up to given block timestamp.
     *
     * <p>The method queries contract_state_change table for the most recent contract state storage value
     * before or equal to the specified block timestamp.
     *
     * <p>The result of the query is then ordered by timestamp in descending order
     * to get the most recent value.
     *
     * @param id             The ID of the contract.
     * @param slot           The slot in the contract's storage.
     * @param blockTimestamp The block timestamp up to which to retrieve the storage value.
     * @return An {@code Optional} containing the byte array of the storage value if found, or an empty {@code Optional} if not.
     */
    @Query(value = """
            select
                coalesce(value_written, value_read) as value
            from contract_state_change
            where contract_id = ?1
            and slot = ?2
            and consensus_timestamp <= ?3
            order by consensus_timestamp desc
            limit 1
            """, nativeQuery = true)
    Optional<byte[]> findStorageByBlockTimestamp(long id, byte[] slot, long blockTimestamp);
}
