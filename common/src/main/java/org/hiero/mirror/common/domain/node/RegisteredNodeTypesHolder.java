// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import java.io.Serializable;
import java.util.List;

/**
 * Single-valued JDBC column type for {@code registered_node.type} ({@code smallint[]}). A bare {@code List} competes
 * with other {@code List}-to-array converters for the same destination type.
 */
public record RegisteredNodeTypesHolder(List<Short> types) implements Serializable {

    public static RegisteredNodeTypesHolder of(List<Short> list) {
        if (list == null) {
            return null;
        }
        return new RegisteredNodeTypesHolder(List.copyOf(list));
    }
}
