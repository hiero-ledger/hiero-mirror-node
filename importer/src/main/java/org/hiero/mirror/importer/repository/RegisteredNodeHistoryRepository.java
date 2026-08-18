// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.node.RegisteredNodeHistory;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface RegisteredNodeHistoryRepository
        extends CrudRepository<RegisteredNodeHistory, Long>, RetentionRepository {

    @Modifying
    @Override
    @Query(value = "delete from registered_node_history where timestamp_range << int8range(:consensusTimestamp, null)")
    int prune(long consensusTimestamp);
}
