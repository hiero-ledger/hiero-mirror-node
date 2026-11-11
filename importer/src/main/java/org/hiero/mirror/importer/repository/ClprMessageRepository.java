// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.ClprMessage;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprMessageRepository extends CrudRepository<ClprMessage, ClprMessage.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from ClprMessage where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
