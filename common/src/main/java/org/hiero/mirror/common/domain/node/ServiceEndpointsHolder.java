// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import java.io.Serializable;
import java.util.List;

/**
 * Single-valued JDBC column type for {@code registered_node.service_endpoints} (JSONB). A bare {@code List} is treated
 * by Spring Data JDBC as a separate aggregate table.
 */
public record ServiceEndpointsHolder(List<RegisteredServiceEndpoint> items) implements Serializable {

    public static ServiceEndpointsHolder of(List<RegisteredServiceEndpoint> list) {
        if (list == null) {
            return null;
        }
        return new ServiceEndpointsHolder(List.copyOf(list));
    }
}
