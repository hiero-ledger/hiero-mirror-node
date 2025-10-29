// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.service.Bound;
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

    @Builder.Default
    Bound hookIds = Bound.EMPTY;

    public List<Bound> getBounds() {
        return hookIds.isEmpty() ? List.of() : List.of(hookIds);
    }
}
