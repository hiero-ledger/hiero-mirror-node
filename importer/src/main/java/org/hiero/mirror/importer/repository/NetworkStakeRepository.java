// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NetworkStakeRepository extends CrudRepository<NetworkStake, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value = "delete from network_stake where consensus_timestamp <= ?1 "
                    + "and epoch_day < (select max(epoch_day) from network_stake) - 366")
    int prune(long consensusTimestamp);
}
