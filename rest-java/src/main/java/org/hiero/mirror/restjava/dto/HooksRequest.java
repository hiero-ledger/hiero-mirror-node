// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@Value
@Builder
public class HooksRequest {

    @Builder.Default
    private final Collection<Long> hookIds = List.of();

    @Builder.Default
    private final int limit = 25;

    @Builder.Default
    private final long lowerBound = 0L;

    @Builder.Default
    private final Sort.Direction order = Direction.DESC;

    private final EntityIdParameter ownerId;

    @Builder.Default
    private final long upperBound = Long.MAX_VALUE;
}
