// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprMessageRepository extends CrudRepository<ClprMessage, ClprMessage.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from clpr_message where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
