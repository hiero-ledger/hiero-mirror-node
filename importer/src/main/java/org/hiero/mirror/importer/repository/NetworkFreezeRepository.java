// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.transaction.NetworkFreeze;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NetworkFreezeRepository extends CrudRepository<NetworkFreeze, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from network_freeze where consensus_timestamp <= :consensusTimestamp", nativeQuery = true)
    int prune(long consensusTimestamp);
}
