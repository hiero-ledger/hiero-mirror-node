// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.LiveHash;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface LiveHashRepository extends CrudRepository<LiveHash, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from LiveHash where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
