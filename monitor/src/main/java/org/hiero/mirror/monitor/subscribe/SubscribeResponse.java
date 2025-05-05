// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class SubscribeResponse {
    private final Instant consensusTimestamp;
    private final Instant publishedTimestamp;
    private final Instant receivedTimestamp;
    private final Scenario<?, ?> scenario;
}
