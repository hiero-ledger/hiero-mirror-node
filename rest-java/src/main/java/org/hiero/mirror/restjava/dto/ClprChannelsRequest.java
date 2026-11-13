// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.rest.model.ClprChannelStatus;
import org.hiero.mirror.restjava.service.Bound;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort.Direction;

/**
 * Input for the Channels API. {@code status} is the API's status, so {@code PENDING} selects pending channel
 * commitments instead of channels.
 */
@Builder
@Value
public class ClprChannelsRequest {

    @Nullable
    private final byte[] channelId;

    @Builder.Default
    private final int limit = 25;

    @Builder.Default
    private final Direction order = Direction.DESC;

    @Nullable
    private final ClprChannelStatus status;

    @Builder.Default
    private final Bound timestamp = Bound.EMPTY;
}
