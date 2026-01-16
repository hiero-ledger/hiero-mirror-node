// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.springframework.data.domain.Sort;

@Data
@Builder
public class NftAllowanceRequest {

    private EntityIdParameter accountId;

    @Builder.Default
    private int limit = 25;

    @Builder.Default
    private Sort.Direction order = Sort.Direction.ASC;

    @Builder.Default
    private boolean isOwner = true;

    @Builder.Default
    private Bound ownerOrSpenderIds = Bound.EMPTY;

    @Builder.Default
    private Bound tokenIds = Bound.EMPTY;

    public List<Bound> getBounds() {
        var primaryBound = !ownerOrSpenderIds.isEmpty() ? ownerOrSpenderIds : tokenIds;
        return tokenIds.isEmpty() ? List.of(primaryBound) : List.of(primaryBound, tokenIds);
    }
}
