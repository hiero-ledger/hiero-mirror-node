// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface AssessedCustomFeeRepository
        extends CrudRepository<AssessedCustomFee, AssessedCustomFee.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(nativeQuery = true, value = "delete from assessed_custom_fee where consensus_timestamp <= ?1")
    int prune(long consensusTimestamp);
}
