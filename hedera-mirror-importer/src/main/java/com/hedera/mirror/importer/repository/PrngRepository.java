// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.transaction.Prng;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface PrngRepository extends CrudRepository<Prng, Long>, RetentionRepository {

    @Modifying
    @Query("delete from Prng where consensusTimestamp <= ?1")
    int prune(long consensusTimestamp);
}
