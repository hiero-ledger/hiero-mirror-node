// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.springframework.data.domain.Sort.Direction;

@Value
@Builder
public class RegisteredNodesRequest {

    @Builder.Default
    private final Collection<Long> nodeIds = List.of();

    @Builder.Default
    private final int limit = 25;

    @Builder.Default
    private final long lowerBound = 0L;

    @Builder.Default
    private final Direction order = Direction.ASC;

    private final RegisteredNodeType type;

    @Builder.Default
    private final long upperBound = Long.MAX_VALUE;
}
