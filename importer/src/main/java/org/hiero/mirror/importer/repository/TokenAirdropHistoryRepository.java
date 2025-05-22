// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.token.AbstractTokenAirdrop;
import org.hiero.mirror.common.domain.token.TokenAirdropHistory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenAirdropHistoryRepository
        extends CrudRepository<TokenAirdropHistory, AbstractTokenAirdrop.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from token_airdrop_history where timestamp_range << int8range(?1, null)", nativeQuery = true)
    int prune(long consensusTimestamp);
}
