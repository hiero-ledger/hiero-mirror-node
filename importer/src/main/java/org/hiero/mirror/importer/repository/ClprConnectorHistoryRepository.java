// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorHistory;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprConnectorHistoryRepository
        extends CrudRepository<ClprConnectorHistory, AbstractClprConnector.Id>, RetentionRepository {

    @Modifying
    @Override
    @Query(
            nativeQuery = true,
            value = "delete from clpr_connector_history where timestamp_range << int8range(?1, null)")
    int prune(long consensusTimestamp);
}
