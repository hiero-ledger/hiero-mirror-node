// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface TokenTransferRepository extends CrudRepository<TokenTransfer, TokenTransfer.Id>, RetentionRepository {

    @Query("select * from token_transfer where consensus_timestamp = :consensusTimestamp")
    List<TokenTransfer> findByConsensusTimestamp(long consensusTimestamp);

    @Modifying
    @Override
    @Query("delete from token_transfer where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
