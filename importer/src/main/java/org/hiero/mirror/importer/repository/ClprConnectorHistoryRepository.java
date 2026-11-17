// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorHistory;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprConnectorHistoryRepository
        extends CrudRepository<ClprConnectorHistory, AbstractClprConnector.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query("delete from clpr_connector_history where timestamp_range << int8range(:consensusTimestamp, null)")
    int prune(long consensusTimestamp);
}
