// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.AbstractTokenAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowanceHistory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAllowanceHistoryRepository
        extends CrudRepository<TokenAllowanceHistory, AbstractTokenAllowance.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value = "delete from token_allowance_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);
}
