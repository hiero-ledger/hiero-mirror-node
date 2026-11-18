// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.hedera.hapi.block.stream.output.protoc.SingletonUpdateChange;
import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprChannelEntry;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprConnectorEntry;
import org.jspecify.annotations.Nullable;

/**
 * CLPR state-change payloads attached to a {@link RecordItem}.
 *
 * <p>{@code clprEndpointManifestUpdate}/{@code clprLedgerConfigurationUpdate} have no plural counterpart since their
 * transaction types can't batch. They carry the raw singleton state change rather than its resolved value since a
 * partial update must be merged against the last known full value -- see {@code ClprStateChangeHandler.process()},
 * which does that merge. {@code ClprStateChangeHandler.process()} iterates the plural collections directly, since a
 * batch/hook-aggregated transaction (e.g. {@code ClprSubmitBundle}) can touch more than one.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Value
public class ClprContext {

    @Builder.Default
    private final Collection<ClprChannelEntry> clprChannels = List.of();

    @Builder.Default
    private final Collection<ClprConnectorEntry> clprConnectors = List.of();

    @Builder.Default
    private final List<ClprConnectorKey> clprConnectorsRemoved = List.of();

    @Nullable
    private final SingletonUpdateChange clprEndpointManifestUpdate;

    @Nullable
    private final SingletonUpdateChange clprLedgerConfigurationUpdate;

    @Builder.Default
    private final List<StateChangeContext.ClprMessageEntry> clprMessages = List.of();

    /**
     * The first entry of {@link #clprChannels}, for single-entity handlers (e.g. {@code ClprCompleteChannel}) that
     * only expect one.
     */
    public @Nullable ClprChannelEntry getClprChannel() {
        final var iterator = clprChannels.iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    /**
     * The first entry of {@link #clprConnectors}, for single-entity handlers (e.g. {@code ClprCompleteConnector})
     * that only expect one.
     */
    public @Nullable ClprConnectorEntry getClprConnector() {
        final var iterator = clprConnectors.iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }
}
