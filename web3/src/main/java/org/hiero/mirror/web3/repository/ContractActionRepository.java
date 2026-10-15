// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ContractActionRepository extends CrudRepository<ContractAction, Long> {

    // Explicit query since consensus_timestamp and index are part of the embedded id, not top-level properties
    @Query("""
            select *
            from contract_action
            where consensus_timestamp = :consensusTimestamp
              and payer_account_id = :payerAccountId
            order by index asc
            """)
    List<ContractAction> findByConsensusTimestampAndPayerAccountIdOrderByIndexAsc(
            long consensusTimestamp, EntityId payerAccountId);

    // result_data_type = 12 corresponds to ContractAction.ResultDataCase.REVERT_REASON (proto field number)
    @Query("""
            select *
            from contract_action
            where consensus_timestamp = :consensusTimestamp
              and payer_account_id = :payerAccountId
              and result_data_type = 12
              and call_type = 4
            order by index asc
            """)
    List<ContractAction> findFailedSystemActionsByConsensusTimestamp(long consensusTimestamp, long payerAccountId);
}
