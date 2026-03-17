// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import java.util.List;

/**
 * Projection for the service_endpoints jsonb column from registered_node.
 */
public interface RegisteredNodeServiceEndpoints {

    List<RegisteredServiceEndpoint> getServiceEndpoints();
}
