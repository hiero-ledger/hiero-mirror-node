// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.LIMIT;
import static org.hiero.mirror.restjava.common.Constants.MAX_LIMIT;
import static org.hiero.mirror.restjava.common.Constants.ORDER;
import static org.hiero.mirror.restjava.common.Constants.REGISTERED_NODE_ID;
import static org.hiero.mirror.restjava.common.Constants.REGISTERED_NODE_TYPE;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.common.domain.node.RegisteredNodeType;
import org.hiero.mirror.restjava.parameter.NumberRangeParameter;
import org.hiero.mirror.restjava.parameter.RestJavaQueryParam;
import org.springframework.data.domain.Sort.Direction;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredNodesRequest {

    @Builder.Default
    @Min(1)
    @Max(MAX_LIMIT)
    @RestJavaQueryParam(name = LIMIT, required = false, defaultValue = DEFAULT_LIMIT)
    private int limit = Integer.parseInt(DEFAULT_LIMIT);

    @Builder.Default
    @RestJavaQueryParam(name = ORDER, required = false, defaultValue = "asc")
    private Direction order = Direction.ASC;

    @Builder.Default
    @RestJavaQueryParam(name = REGISTERED_NODE_ID, required = false)
    @Size(max = 2)
    private List<NumberRangeParameter> registeredNodeIds = List.of();

    @RestJavaQueryParam(name = REGISTERED_NODE_TYPE, required = false)
    private RegisteredNodeType type;
}
