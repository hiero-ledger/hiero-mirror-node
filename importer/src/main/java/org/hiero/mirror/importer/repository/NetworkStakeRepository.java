// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface NetworkStakeRepository extends CrudRepository<NetworkStake, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            value = "delete from network_stake where consensus_timestamp <= :consensusTimestamp "
                    + "and epoch_day < (select max(epoch_day) from network_stake) - 366")
    int prune(long consensusTimestamp);
}
