// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import static org.hiero.mirror.restjava.common.Constants.DEFAULT_LIMIT;

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
    byte[] channelId;

    @Builder.Default
    int limit = Integer.parseInt(DEFAULT_LIMIT);

    @Builder.Default
    Direction order = Direction.DESC;

    @Nullable
    ClprChannelStatus status;

    @Builder.Default
    Bound timestamp = Bound.EMPTY;
}
