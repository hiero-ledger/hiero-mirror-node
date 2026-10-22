// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.restjava.dto.ClprConnectorsRequest;

public interface ClprConnectorRepositoryCustom extends JooqRepository {

    List<ClprConnector> findAll(ClprConnectorsRequest request);
}
