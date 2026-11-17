// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ClprConnectorRepository extends CrudRepository<ClprConnector, AbstractClprConnector.Id> {

    // Explicit query since channel_id is part of the embedded id, not a top-level property
    @Query("select * from clpr_connector where channel_id = :channelId and connector_id = :connectorId")
    Optional<ClprConnector> findByChannelIdAndConnectorId(byte[] channelId, byte[] connectorId);
}
