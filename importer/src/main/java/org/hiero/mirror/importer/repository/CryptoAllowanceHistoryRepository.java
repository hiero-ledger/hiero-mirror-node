// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.AbstractCryptoAllowance;
import org.hiero.mirror.common.domain.entity.CryptoAllowanceHistory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface CryptoAllowanceHistoryRepository
        extends CrudRepository<CryptoAllowanceHistory, AbstractCryptoAllowance.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value = "delete from crypto_allowance_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);
}
