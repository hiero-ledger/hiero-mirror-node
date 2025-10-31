// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.Collection;
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
    int limit = 25;

    @Builder.Default
    Sort.Direction order = Direction.DESC;

    Collection<Long> hookIdEqualsFilters;

    Long hookIdLowerBoundInclusive;

    Long hookIdUpperBoundInclusive;
}
