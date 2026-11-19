// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.springframework.data.domain.Sort.Direction;

@Builder
@Value
public class HookStorageRequest {

    private final long hookId;

    private final byte[] keyLowerBound;

    @Builder.Default
    private final Collection<byte[]> keys = List.of();

    private final byte[] keyUpperBound;

    @Builder.Default
    private final int limit = 25;

    @Builder.Default
    private final Direction order = Direction.ASC;

    private final EntityIdParameter ownerId;

    @Builder.Default
    private final Bound timestamp = Bound.EMPTY;

    public List<byte[]> getKeysInRange() {
        if (keys.isEmpty()) {
            return List.of();
        }

        return keys.stream()
                .filter(key -> Arrays.compareUnsigned(key, keyLowerBound) >= 0
                        && Arrays.compareUnsigned(key, keyUpperBound) <= 0)
                .toList();
    }

    public boolean isHistorical() {
        return !timestamp.isEmpty();
    }
}
