// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.AbstractTokenAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowanceHistory;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAllowanceHistoryRepository
        extends CrudRepository<TokenAllowanceHistory, AbstractTokenAllowance.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from token_allowance_history where timestamp_range << int8range(:consensusTimestamp, null)")
    int prune(long consensusTimestamp);
}
