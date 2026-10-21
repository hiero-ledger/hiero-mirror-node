// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.springframework.data.repository.CrudRepository;

/**
 * Queries filtered by {@code channel_id}, the distribution column in {@code v2}, route to a single shard. The others
 * scatter-gather across all shards.
 */
public interface ClprConnectorRepository
        extends CrudRepository<ClprConnector, AbstractClprConnector.Id>, ClprConnectorRepositoryCustom {}
