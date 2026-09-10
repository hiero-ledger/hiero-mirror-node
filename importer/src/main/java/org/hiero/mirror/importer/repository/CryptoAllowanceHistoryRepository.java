// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.AbstractCryptoAllowance;
import org.hiero.mirror.common.domain.entity.CryptoAllowanceHistory;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface CryptoAllowanceHistoryRepository
        extends CrudRepository<CryptoAllowanceHistory, AbstractCryptoAllowance.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from crypto_allowance_history where timestamp_range << int8range(:consensusTimestamp, null)")
    int prune(@Param("consensusTimestamp") long consensusTimestamp);
}
