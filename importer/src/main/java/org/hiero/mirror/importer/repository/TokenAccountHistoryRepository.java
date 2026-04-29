// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccountHistory;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;

public interface TokenAccountHistoryRepository
        extends CrudRepository<TokenAccountHistory, AbstractTokenAccount.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from token_account_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);
}
