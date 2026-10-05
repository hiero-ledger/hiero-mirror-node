// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.TreeSet;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.springframework.data.domain.Sort.Direction;

@Value
@Builder
public class HooksRequest {

    TreeSet<Long> hookIds;
    long lowerBound;
    int limit;
    Direction order;
    EntityIdParameter ownerId;
    long upperBound;
}
