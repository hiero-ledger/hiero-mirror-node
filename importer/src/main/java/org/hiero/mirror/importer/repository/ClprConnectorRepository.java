// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.springframework.data.repository.CrudRepository;

public interface ClprConnectorRepository extends CrudRepository<ClprConnector, AbstractClprConnector.Id> {

    Optional<ClprConnector> findByChannelIdAndConnectorId(byte[] channelId, byte[] connectorId);
}
