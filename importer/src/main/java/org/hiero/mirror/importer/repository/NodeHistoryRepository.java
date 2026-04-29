// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.node.NodeHistory;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.CrudRepository;

public interface NodeHistoryRepository extends CrudRepository<NodeHistory, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from node_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);
}
