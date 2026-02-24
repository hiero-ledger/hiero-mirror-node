// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.restjava.parameter.EntityIdRangeParameter;
import org.hiero.mirror.restjava.parameter.RestJavaQueryParam;
import org.springframework.data.domain.Sort.Direction;

/**
 * Network nodes request DTO for testing annotation framework.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkNodeRequest {

    public static final int MAX_LIMIT = 25;
    public static final int DEFAULT_LIMIT = 10;

    @RestJavaQueryParam(name = "file.id", required = false, defaultValue = "102")
    private EntityIdRangeParameter fileId;

    @RestJavaQueryParam(name = "node.id", required = false)
    @Builder.Default
    private List<EntityIdRangeParameter> nodeId = List.of();

    @RestJavaQueryParam(name = "limit", defaultValue = "10")
    @Min(1)
    private int limit;

    @RestJavaQueryParam(name = "order", defaultValue = "ASC")
    private Direction order;

    /**
     * Gets the effective limit, capped at MAX_LIMIT. Matches rest module behavior where limit is capped at 25 for
     * network nodes.
     */
    public int getEffectiveLimit() {
        return Math.min(limit, MAX_LIMIT);
    }
}
