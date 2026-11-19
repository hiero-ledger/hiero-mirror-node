// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.clpr;

import java.util.List;
import org.hiero.mirror.common.domain.ListHolder;

/**
 * Single-valued JDBC column type for {@code clpr_channel.endpoint_manifest} and
 * {@code clpr_endpoint_manifest.endpoints} (JSONB). A bare {@code List} is treated by Spring Data JDBC as a separate
 * aggregate table.
 */
public record ClprEndpointListHolder(List<ClprEndpoint> items) implements ListHolder<ClprEndpoint> {

    public static ClprEndpointListHolder of(List<ClprEndpoint> list) {
        if (list == null) {
            return null;
        }
        return new ClprEndpointListHolder(List.copyOf(list));
    }
}
