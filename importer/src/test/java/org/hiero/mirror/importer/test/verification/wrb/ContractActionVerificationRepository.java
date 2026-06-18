// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.importer.repository.ContractActionRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ContractActionVerificationRepository extends ContractActionRepository {

    @Query("select a from ContractAction a where a.consensusTimestamp <= :ts order by a.consensusTimestamp, a.index")
    List<ContractAction> findUpTo(@Param("ts") long ts);
}
