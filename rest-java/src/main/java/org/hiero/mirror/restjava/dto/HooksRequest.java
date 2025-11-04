// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.Collection;
import java.util.Collections;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@Value
@Builder
public class HooksRequest {
    EntityIdParameter ownerId;

    @Builder.Default
    private final int limit = 25;

    @Builder.Default
    private final Sort.Direction order = Direction.DESC;

    @Builder.Default
    private final Collection<Long> hookIdEqualsFilters = Collections.emptyList();

    @Builder.Default
    private final long hookIdLowerBoundInclusive = 0L;

    @Builder.Default
    private final long hookIdUpperBoundInclusive = Long.MAX_VALUE;
}
