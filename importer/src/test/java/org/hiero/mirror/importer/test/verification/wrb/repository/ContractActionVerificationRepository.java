// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb.repository;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.importer.repository.ContractActionRepository;

@WrbRepository
public interface ContractActionVerificationRepository extends ContractActionRepository {

    List<ContractAction> findAllByConsensusTimestampLessThanEqualOrderByConsensusTimestampAscIndexAsc(long timestamp);
}
