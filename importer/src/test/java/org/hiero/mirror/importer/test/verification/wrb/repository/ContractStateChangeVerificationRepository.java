// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.importer.repository.ContractStateChangeRepository;

@WrbRepository
public interface ContractStateChangeVerificationRepository extends ContractStateChangeRepository {

    List<ContractStateChange> findAllByConsensusTimestampLessThanEqualOrderByConsensusTimestampAscContractIdAscSlotAsc(
            long consensusTimestamp);
}
