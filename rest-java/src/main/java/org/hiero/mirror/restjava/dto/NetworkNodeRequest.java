// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.List;
import lombok.Data;
import org.hiero.mirror.restjava.common.RestJavaQueryParam;
import org.hiero.mirror.restjava.parameter.EntityIdRangeParameter;
import org.springframework.data.domain.Sort.Direction;

/**
 * Network nodes request DTO for testing annotation framework.
 */
@Data
public class NetworkNodeRequest {

    @RestJavaQueryParam(name = "file.id", defaultValue = "102", required = false)
    long fileId;

    @RestJavaQueryParam(name = "node.id", required = false)
    List<EntityIdRangeParameter> nodeId;

    @RestJavaQueryParam(name = "limit", defaultValue = "25")
    int limit;

    @RestJavaQueryParam(name = "order", defaultValue = "ASC")
    Direction order;
}
