// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import java.io.Serializable;
import java.util.List;

/**
 * Single-valued JDBC column type for {@code node.associated_registered_nodes} ({@code bigint[]}). A bare {@code List}
 * competes with other {@code List}-to-array converters for the same destination type.
 */
public record AssociatedRegisteredNodeIds(List<Long> ids) implements Serializable {

    public static AssociatedRegisteredNodeIds of(List<Long> list) {
        if (list == null) {
            return null;
        }
        return new AssociatedRegisteredNodeIds(List.copyOf(list));
    }
}
