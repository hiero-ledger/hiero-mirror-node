// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb;

import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.importer.repository.ContractStateChangeRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ContractStateChangeVerificationRepository extends ContractStateChangeRepository {

    @Query("select c from ContractStateChange c where c.consensusTimestamp <= :ts"
            + " order by c.consensusTimestamp, c.contractId, c.slot")
    List<ContractStateChange> findUpTo(@Param("ts") long ts);
}
