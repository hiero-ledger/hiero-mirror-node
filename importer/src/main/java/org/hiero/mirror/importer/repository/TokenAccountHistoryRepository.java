// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccountHistory;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAccountHistoryRepository
        extends CrudRepository<TokenAccountHistory, AbstractTokenAccount.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from token_account_history where timestamp_range << int8range(:consensusTimestamp, null)")
    int prune(long consensusTimestamp);
}
