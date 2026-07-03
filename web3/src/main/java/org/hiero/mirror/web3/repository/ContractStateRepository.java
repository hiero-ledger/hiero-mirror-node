// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ContractStateRepository extends CrudRepository<ContractState, Long> {

    @Query(value = "select value from contract_state where contract_id = ?1 and slot =?2", nativeQuery = true)
    Optional<byte[]> findStorage(final Long contractId, final byte[] key);

    @Query(value = """
                    select slot, value from contract_state
                    where contract_id = :contractId
                    and slot >= :minSlot
                    and slot <= :maxSlot
                    """, nativeQuery = true)
    List<ContractSlotValue> findStorageRange(
            @Param("contractId") Long contractId, @Param("minSlot") byte[] minSlot, @Param("maxSlot") byte[] maxSlot);

    @Query(value = """
                    select slot, value from contract_state
                    where contract_id = :contractId
                    and slot = ANY(CAST(:slots AS bytea[]))
                    """, nativeQuery = true)
    List<ContractSlotValue> findStorageBatch(@Param("contractId") Long contractId, @Param("slots") byte[][] slots);

    @Query(value = """
                    select slot, value from contract_state
                    where contract_id = :contractId
                    and slot >= decode(repeat('00', 32), 'hex')
                    and slot <= decode(lpad(to_hex(:maxSlotIndex), 64, '0'), 'hex')
                    """, nativeQuery = true)
    List<ContractSlotValue> findInitialStorageSlots(
            @Param("contractId") Long contractId, @Param("maxSlotIndex") int maxSlotIndex);

    @Query(value = """
                    select slot, value from contract_state
                    where contract_id = :contractId
                    order by slot asc
                    limit :limit
                    """, nativeQuery = true)
    List<ContractSlotValue> findFirstStorageSlots(@Param("contractId") Long contractId, @Param("limit") int limit);

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
