// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.common.domain.clpr.ClprChannelStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@Builder
@NullMarked
@Value
public class ClprChannelsRequest {

    @Nullable
    byte[] channelId;

    @Builder.Default
    int limit = 25;

    @Builder.Default
    long lowerBound = 0L;

    @Builder.Default
    Sort.Direction order = Direction.DESC;

    @Nullable
    ClprChannelStatus status;

    @Builder.Default
    long upperBound = Long.MAX_VALUE;
}
