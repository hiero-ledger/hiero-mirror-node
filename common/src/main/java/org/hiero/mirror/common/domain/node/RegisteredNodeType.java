// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.node;

import lombok.Getter;

@Getter
public enum RegisteredNodeType {
    UNKNOWN(0),
    BLOCK_NODE(1),
    GENERAL_SERVICE(2),
    MIRROR_NODE(3),
    RPC_RELAY(4);

    private final short id;

    RegisteredNodeType(final int id) {
        this.id = (short) id;
    }
}
