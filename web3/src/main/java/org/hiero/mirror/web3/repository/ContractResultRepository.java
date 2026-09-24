// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.Collection;
import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractResultRepository extends CrudRepository<ContractResult, Long> {

    // Returns the consensus timestamps among the given candidates whose result produced EVM output (non-empty
    // function_result), i.e. that actually executed. Used to prefer a genuine execution over a pre-execution failure
    // result sharing a transaction hash. Batched into one query so a hash shared by many results costs a single lookup.
    @Query(
            value = "select consensus_timestamp from contract_result where consensus_timestamp in (:timestamps) "
                    + "and function_result is not null and octet_length(function_result) > 0",
            nativeQuery = true)
    List<Long> findExecutedTimestamps(Collection<Long> timestamps);
}
