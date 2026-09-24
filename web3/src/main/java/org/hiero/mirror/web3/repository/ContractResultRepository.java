// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Collection;
import java.util.Optional;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractResultRepository extends CrudRepository<ContractResult, Long> {

    // Returns the latest consensus timestamp among the given candidates whose result produced EVM output (non-empty
    // function_result), i.e. that actually executed. Used to prefer a genuine execution over a pre-execution failure
    // result sharing a transaction hash, picking the latest when several executed. Batched into one query so a hash
    // shared by many results costs a single lookup. contract_id is the citus distribution column of contract_result
    // and equals contract_transaction_hash.entity_id (see ContractResult.toContractTransactionHash), so constraining on
    // it lets citus prune shards instead of scanning every one.
    @Query(
            value = "select consensus_timestamp from contract_result where consensus_timestamp in (:timestamps) "
                    + "and contract_id in (:contractIds) "
                    + "and function_result is not null and octet_length(function_result) > 0 "
                    + "order by consensus_timestamp desc limit 1",
            nativeQuery = true)
    Optional<Long> findLatestExecutedTimestamp(Collection<Long> timestamps, Collection<Long> contractIds);
}
