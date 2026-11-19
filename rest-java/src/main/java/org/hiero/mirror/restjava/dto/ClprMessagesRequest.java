// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;

import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.rest.model.ClprMessageType;
import org.hiero.mirror.restjava.service.Bound;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort.Direction;

/** Input for the Messages API, which is always scoped to a single channel. */
@Builder
@Value
public class ClprMessagesRequest {

    byte[] channelId;

    @Nullable
    byte[] connectorId;

    @Builder.Default
    int limit = Integer.parseInt(DEFAULT_LIMIT);

    @Builder.Default
    Direction order = Direction.DESC;

    @Builder.Default
    Bound timestamp = Bound.EMPTY;

    @Nullable
    ClprMessageType type;
}
