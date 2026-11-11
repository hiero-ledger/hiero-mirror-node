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
 * <p>{@code clprEndpointManifest}/{@code clprLedgerConfiguration} have no plural counterpart since their transaction
 * types can't batch. {@code ClprStateChangeHandler.process()} iterates the plural lists directly, since a
 * batch/hook-aggregated transaction (e.g. {@code ClprSubmitBundle}) can touch more than one.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Value
public class ClprContext {

    @Builder.Default
    private final List<ClprChannel> clprChannels = List.of();

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

    /**
     * The first entry of {@link #clprChannels}, for single-entity handlers (e.g. {@code ClprCompleteChannel}) that
     * only expect one.
     */
    public @Nullable ClprChannel getClprChannel() {
        return clprChannels.isEmpty() ? null : clprChannels.getFirst();
    }

    /**
     * The first entry of {@link #clprConnectors}, for single-entity handlers (e.g. {@code ClprCompleteConnector})
     * that only expect one.
     */
    public @Nullable ClprConnector getClprConnector() {
        return clprConnectors.isEmpty() ? null : clprConnectors.getFirst();
    }
}
