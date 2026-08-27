// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractStateChangeRepository extends CrudRepository<ContractStateChange, ContractStateChange.Id> {

    List<ContractStateChange> findByConsensusTimestamp(long consensusTimestamp);

    @Query(value = """
            select *
            from contract_state_change
            where consensus_timestamp = ?1
              and contract_id in (
                  select distinct contract_id
                  from contract_state_change
                  where consensus_timestamp = ?1
                  order by contract_id
                  limit ?2
              )
            order by contract_id, slot
            limit ?3 offset ?4
            """, nativeQuery = true)
    List<ContractStateChange> findByConsensusTimestamp(
            long consensusTimestamp, int accountLimit, int limit, int offset);

    /**
     * Finds state changes where the value was actually modified (value_written differs from value_read).
     * Uses PostgreSQL's IS DISTINCT FROM to correctly handle NULL comparisons.
     */
    @Query(value = """
            select * from contract_state_change
            where consensus_timestamp = ?1
              and value_written is distinct from value_read
              and contract_id in (
                  select distinct contract_id
                  from contract_state_change
                  where consensus_timestamp = ?1
                    and value_written is distinct from value_read
                  order by contract_id
                  limit ?2
              )
            order by contract_id, slot
            limit ?3 offset ?4
            """, nativeQuery = true)
    List<ContractStateChange> findModifiedByConsensusTimestamp(
            long consensusTimestamp, int accountLimit, int limit, int offset);
}
