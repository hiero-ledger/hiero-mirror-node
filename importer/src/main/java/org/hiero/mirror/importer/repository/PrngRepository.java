// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.Prng;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface PrngRepository extends CrudRepository<Prng, Long>, RetentionRepository {

    @Modifying
    @Query("delete from prng where consensus_timestamp <= :consensusTimestamp")
    int prune(long consensusTimestamp);
}
