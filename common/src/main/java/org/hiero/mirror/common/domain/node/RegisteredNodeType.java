// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum RegisteredNodeType {
    BLOCK_NODE(0),
    GENERAL_SERVICE(1),
    MIRROR_NODE(2),
    RPC_RELAY(3);

    private static final Map<Short, RegisteredNodeType> ID_MAP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(RegisteredNodeType::getId, Function.identity()));

    private final short id;

    RegisteredNodeType(int id) {
        this.id = (short) id;
    }

    public static RegisteredNodeType fromId(short id) {
        RegisteredNodeType type = ID_MAP.get(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown NodeType id: " + id);
        }
        return type;
    }
}
