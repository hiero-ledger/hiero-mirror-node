// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.rest.model.ClprMessageType;
import org.hiero.mirror.restjava.service.Bound;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort.Direction;

/** Input for the Messages API, which is always scoped to a single channel and requires a timestamp. */
@Builder
@Value
public class ClprMessagesRequest {

    private final byte[] channelId;

    @Nullable
    private final byte[] connectorId;

    @Builder.Default
    private final int limit = 25;

    @Builder.Default
    private final Direction order = Direction.DESC;

    @Builder.Default
    private final Bound timestamp = Bound.EMPTY;

    @Nullable
    private final ClprMessageType type;
}
