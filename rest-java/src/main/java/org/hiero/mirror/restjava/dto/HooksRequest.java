// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@Data
@Builder
public class HooksRequest {
    private EntityIdParameter accountId;

    @Builder.Default
    private int limit = 25;

    @Builder.Default
    private Sort.Direction order = Direction.DESC;

    @Builder.Default
    private Bound hookIds = Bound.EMPTY;

    public List<Bound> getBounds() {
        return hookIds.isEmpty() ? List.of() : List.of(hookIds);
    }
}
