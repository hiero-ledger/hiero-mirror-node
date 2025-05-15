// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import com.hedera.hashgraph.sdk.Transaction;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.hiero.mirror.monitor.NodeProperties;

@Builder(toBuilder = true)
@Data
public class PublishRequest {
    private final boolean receipt;
    private final boolean sendRecord;
    private final PublishScenario scenario;
    private final Instant timestamp;
    private final Transaction<?> transaction;
    private NodeProperties node;
}
