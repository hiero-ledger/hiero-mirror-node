// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ClprChannel;
import com.hederahashgraph.api.proto.java.ClprConnector;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import com.hederahashgraph.api.proto.java.ClprEndpointManifest;
import com.hederahashgraph.api.proto.java.ClprLedgerConfiguration;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * CLPR state-change payloads attached to a {@link RecordItem}, kept in one place rather than as individual fields
 * on {@code RecordItem} since the vast majority of record items have no CLPR activity.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Value
public class ClprRecordContext {

    @Nullable
    private final ClprChannel clprChannel;

    @Nullable
    private final ByteString clprChannelPendingCommitment;

    @Nullable
    private final ClprConnector clprConnector;

    @Nullable
    private final ClprEndpointManifest clprEndpointManifest;

    @Nullable
    private final ClprLedgerConfiguration clprLedgerConfiguration;

    @Builder.Default
    private final List<ClprChannel> clprChannels = List.of();

    @Builder.Default
    private final List<StateChangeContext.ClprMessageEntry> clprMessages = List.of();

    @Builder.Default
    private final List<ClprConnector> clprConnectors = List.of();

    @Builder.Default
    private final List<ClprConnectorKey> clprConnectorsRemoved = List.of();
}
