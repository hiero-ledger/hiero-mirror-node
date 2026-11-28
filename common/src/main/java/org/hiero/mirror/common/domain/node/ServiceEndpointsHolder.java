// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import java.util.List;
import org.hiero.mirror.common.domain.ListHolder;

/**
 * Single-valued JDBC column type for {@code registered_node.service_endpoints} (JSONB). A bare {@code List} is treated
 * by Spring Data JDBC as a separate aggregate table.
 */
public record ServiceEndpointsHolder(List<RegisteredServiceEndpoint> items)
        implements ListHolder<RegisteredServiceEndpoint> {

    public static ServiceEndpointsHolder of(List<RegisteredServiceEndpoint> list) {
        if (list == null) {
            return null;
        }
        return new ServiceEndpointsHolder(List.copyOf(list));
    }
}
