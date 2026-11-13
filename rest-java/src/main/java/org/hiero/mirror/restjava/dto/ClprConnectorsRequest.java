// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.dto;

import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.rest.model.ClprConnectorStatus;
import org.hiero.mirror.restjava.service.Bound;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort.Direction;

/** Input for the Connectors API, which is always scoped to a single channel. */
@Builder
@Value
public class ClprConnectorsRequest {

    private final byte[] channelId;

    @Nullable
    private final byte[] connectorId;

    @Builder.Default
    private final int limit = 25;

    @Builder.Default
    private final Direction order = Direction.DESC;

    @Nullable
    private final ClprConnectorStatus status;

    @Builder.Default
    private final Bound timestamp = Bound.EMPTY;
}
