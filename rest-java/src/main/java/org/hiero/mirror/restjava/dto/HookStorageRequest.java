// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import static org.hiero.mirror.restjava.common.Constants.CONSENSUS_TIMESTAMP;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    public PageRequest getPageRequest() {
        Sort sort;

        if (isHistorical()) {
            sort = Sort.by(new Sort.Order(order, Constants.KEY), new Sort.Order(Direction.DESC, CONSENSUS_TIMESTAMP));
        } else {
            sort = Sort.by(order, Constants.KEY);
        }

        return PageRequest.of(0, limit, sort);
    }

    public boolean isHistorical() {
        return !timestamp.isEmpty();
    }
}
