// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.clpr.AbstractClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.springframework.data.repository.CrudRepository;

public interface ClprConnectorRepository extends CrudRepository<ClprConnector, AbstractClprConnector.Id> {}
