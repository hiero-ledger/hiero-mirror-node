// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.common.domain.clpr.ClprMessageType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@Builder
@NullMarked
@Value
public class ClprMessagesRequest {

    byte[] channelId;

    @Nullable
    byte[] connectorId;

    @Builder.Default
    int limit = 25;

    @Builder.Default
    long messageIdLowerBound = 0L;

    @Builder.Default
    long messageIdUpperBound = Long.MAX_VALUE;

    @Builder.Default
    Sort.Direction order = Direction.DESC;

    @Builder.Default
    long timestampLowerBound = 0L;

    @Builder.Default
    long timestampUpperBound = Long.MAX_VALUE;

    @Nullable
    ClprMessageType type;
}
