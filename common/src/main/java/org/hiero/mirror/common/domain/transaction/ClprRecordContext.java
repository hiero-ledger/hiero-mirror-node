// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

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
 * CLPR state-change payloads attached to a {@link RecordItem}.
 *
 * <p>{@code clprChannel}/{@code clprConnector} are the first entry of {@code clprChannels}/{@code clprConnectors},
 * kept for single-entity handlers (e.g. {@code ClprCompleteChannel}) that only expect one. {@code
 * ClprStateChangeHandler.process()} iterates the plural lists instead, since a batch/hook-aggregated transaction
 * (e.g. {@code ClprSubmitBundle}) can touch more than one. {@code clprEndpointManifest}/{@code
 * clprLedgerConfiguration} have no plural counterpart since their transaction types can't batch.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Value
public class ClprRecordContext {

    @Nullable
    private final ClprChannel clprChannel;

    @Builder.Default
    private final List<ClprChannel> clprChannels = List.of();

    @Nullable
    private final ClprConnector clprConnector;

    @Builder.Default
    private final List<ClprConnector> clprConnectors = List.of();

    @Builder.Default
    private final List<ClprConnectorKey> clprConnectorsRemoved = List.of();

    @Nullable
    private final ClprEndpointManifest clprEndpointManifest;

    @Nullable
    private final ClprLedgerConfiguration clprLedgerConfiguration;

    @Builder.Default
    private final List<StateChangeContext.ClprMessageEntry> clprMessages = List.of();
}
