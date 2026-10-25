// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractStateChangeRepository extends CrudRepository<ContractStateChange, ContractStateChange.Id> {

    @Query(value = """
            select *
            from contract_state_change
            where consensus_timestamp = ?1
              and (contract_id > ?2 or (contract_id = ?2 and slot > ?3))
            order by contract_id, slot
            limit ?4
            """, nativeQuery = true)
    List<ContractStateChange> findByConsensusTimestamp(
            long consensusTimestamp, long contractId, byte[] slot, int limit);

    /**
     * Finds state changes where the value was actually modified (value_written differs from value_read).
     * Uses PostgreSQL's IS DISTINCT FROM to correctly handle NULL comparisons. Pages by the
     * (contract_id, slot) primary-key order: pass a key strictly before the first row to start, then the last row of
     * the previous page.
     */
    @Query(value = """
            select *
            from contract_state_change
            where consensus_timestamp = ?1
              and value_written is distinct from value_read
              and (contract_id > ?2 or (contract_id = ?2 and slot > ?3))
            order by contract_id, slot
            limit ?4
            """, nativeQuery = true)
    List<ContractStateChange> findModifiedByConsensusTimestamp(
            long consensusTimestamp, long contractId, byte[] slot, int limit);
}
