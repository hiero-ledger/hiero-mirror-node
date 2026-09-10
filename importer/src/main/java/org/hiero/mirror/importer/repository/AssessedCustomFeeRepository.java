// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.AssessedCustomFee;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface AssessedCustomFeeRepository
        extends CrudRepository<AssessedCustomFee, AssessedCustomFee.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from assessed_custom_fee where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
