// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ClprLedgerConfiguration;
import com.hederahashgraph.api.proto.java.ClprThrottles;
import org.junit.jupiter.api.Test;

class ClprThrottleValuesTest {

    @Test
    void fromThrottlesWidensUint32Fields() {
        // given -- -1 is uint32 max; a plain (long) cast would sign-extend it to -1 instead of 4294967295
        final var throttles = ClprThrottles.newBuilder()
                .setMaxLocalEndpoints(-1)
                .setMaxMessagePayloadBytes(-1)
                .setMaxMessagesPerBundle(-1)
                .setMaxPeerEndpoints(-1)
                .setMaxQueueDepth(-1)
                .build();

        // when
        final var throttleValues = ClprThrottleValues.from(throttles);

        // then
        assertThat(throttleValues)
                .returns(4_294_967_295L, ClprThrottleValues::maxLocalEndpoints)
                .returns(4_294_967_295L, ClprThrottleValues::maxMessagePayloadBytes)
                .returns(4_294_967_295L, ClprThrottleValues::maxMessagesPerBundle)
                .returns(4_294_967_295L, ClprThrottleValues::maxPeerEndpoints)
                .returns(4_294_967_295L, ClprThrottleValues::maxQueueDepth);
    }

    @Test
    void fromLedgerConfigurationWithoutThrottlesReturnsNull() {
        assertThat(ClprThrottleValues.from(ClprLedgerConfiguration.getDefaultInstance()))
                .isNull();
    }
}
