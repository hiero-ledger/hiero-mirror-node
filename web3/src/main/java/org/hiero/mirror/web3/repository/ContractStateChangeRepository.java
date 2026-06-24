// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.springframework.data.repository.CrudRepository;

public interface ContractStateChangeRepository extends CrudRepository<ContractStateChange, ContractStateChange.Id> {

    List<ContractStateChange> findByConsensusTimestamp(long consensusTimestamp);
}
