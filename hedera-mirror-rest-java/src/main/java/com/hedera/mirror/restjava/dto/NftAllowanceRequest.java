// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.dto;

import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.service.Bound;
import java.util.List;
import lombok.Builder;
import lombok.Data;
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
