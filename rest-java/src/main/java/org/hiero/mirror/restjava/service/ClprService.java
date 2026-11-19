// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.List;
import org.hiero.mirror.rest.model.ClprChannel;
import org.hiero.mirror.restjava.dto.ClprChannelsRequest;
import org.hiero.mirror.restjava.dto.ClprMessagesRequest;
import org.hiero.mirror.restjava.dto.ClprMessagesResult;

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
     * Returns the channel's messages as the API model, ordered by consensus timestamp. The timestamp range is bounded,
     * since messages are partitioned by it: an open side defaults to the channel's creation or now, and a range wider
     * than the maximum is truncated to its newest part when descending or its oldest part when ascending. The result
     * says where to continue from when it was truncated. With a {@code connectorId}, only the messages dispatched for
     * that connector, which are the Data messages, are returned.
     */
    ClprMessagesResult getMessages(ClprMessagesRequest request);
}
