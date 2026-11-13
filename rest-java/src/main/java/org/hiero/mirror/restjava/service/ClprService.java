// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.List;
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.rest.model.ClprEndpointManifest;
import org.hiero.mirror.rest.model.ClprLedgerConfiguration;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;

public interface ClprService {

    /**
     * Returns channels as the API model. With {@code status=PENDING}, the channel commitments registered but not yet
     * completed or abandoned, which have no {@code channel_id}, so a request also carrying one is rejected. Otherwise,
     * with a {@code channelId} and no timestamp, that channel's current state; with a {@code channelId} and a
     * timestamp, its state as of that timestamp; without a {@code channelId}, the channels whose creation timestamp is
     * within the timestamp bounds, ordered by creation timestamp. Without a status and a {@code channelId}, those
     * channels are merged with the open pending channel commitments.
     */
    List<ClprChannel> getChannels(ClprChannelsRequest request);

    /**
     * Returns the latest endpoint manifest within the timestamp bounds.
     *
     * @throws jakarta.persistence.EntityNotFoundException if there is none
     */
    ClprEndpointManifest getEndpointManifest(Bound timestamp);

    /**
     * Returns the latest ledger configuration within the timestamp bounds.
     *
     * @throws jakarta.persistence.EntityNotFoundException if there is none
     */
    ClprLedgerConfiguration getLedgerConfiguration(Bound timestamp);
}
