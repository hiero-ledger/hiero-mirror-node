// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@Builder
@NullMarked
@Value
public class ClprPendingRequest {

    @Builder.Default
    int limit = 25;

    @Builder.Default
    long lowerBound = 0L;

    @Builder.Default
    Sort.Direction order = Direction.DESC;

    @Builder.Default
    long upperBound = Long.MAX_VALUE;
}
