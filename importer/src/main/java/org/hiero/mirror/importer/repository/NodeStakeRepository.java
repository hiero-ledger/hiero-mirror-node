// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.List;
import org.hiero.mirror.common.domain.addressbook.NodeStake;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface NodeStakeRepository extends CrudRepository<NodeStake, NodeStake.Id>, RetentionRepository {

    @Query(
            value = "select * from node_stake where consensus_timestamp = (select max(consensus_timestamp) from "
                    + "node_stake)",
            nativeQuery = true)
    List<NodeStake> findLatest();

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value = "delete from node_stake where consensus_timestamp <= ?1 "
                    + "and epoch_day < (select max(epoch_day) from node_stake) - 366")
    int prune(long consensusTimestamp);
}
