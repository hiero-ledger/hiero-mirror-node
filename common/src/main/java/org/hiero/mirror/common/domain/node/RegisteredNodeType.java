// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import lombok.Getter;

@Getter
public enum RegisteredNodeType {
    BLOCK_NODE(0),
    GENERAL_SERVICE(1),
    MIRROR_NODE(2),
    RPC_RELAY(3);

    private final short id;

    RegisteredNodeType(int id) {
        this.id = (short) id;
    }

    public short getValue() {
        return id;
    }
}
