// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenTransferRepository extends CrudRepository<TokenTransfer, TokenTransfer.Id>, RetentionRepository {

    @Query(nativeQuery = true, value = "select * from token_transfer where consensus_timestamp = ?1")
    List<TokenTransfer> findByConsensusTimestamp(long consensusTimestamp);

    @Modifying
    @Override
    @Query(nativeQuery = true, value = "delete from token_transfer where consensus_timestamp <= ?1")
    int prune(long consensusTimestamp);
}
