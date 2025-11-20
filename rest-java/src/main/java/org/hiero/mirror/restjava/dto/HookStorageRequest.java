// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.springframework.data.domain.Sort.Direction;

@Value
@Builder
public class HookStorageRequest {

    private final long hookId;

    private final byte[] keyLowerBound;

    @Builder.Default
    private final Collection<String> keys = List.of();

    private final byte[] keyUpperBound;

    @Builder.Default
    private final int limit = 25;

    @Builder.Default
    private final Direction order = Direction.ASC;

    private final EntityIdParameter ownerId;

    @Builder.Default
    private final Bound timestamp = Bound.EMPTY;
}
