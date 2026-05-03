// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.springframework.data.domain.Sort.Direction;

@Value
@Builder
public class HookStorageRequest {

    long hookId;
    List<byte[]> keys;
    byte[] keyLowerBound;
    byte[] keyUpperBound;
    int limit;
    Direction order;
    EntityIdParameter ownerId;
    Bound timestamp;
}
