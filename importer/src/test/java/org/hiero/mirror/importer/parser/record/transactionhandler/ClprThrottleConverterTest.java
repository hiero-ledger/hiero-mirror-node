// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ClprLedgerConfiguration;
import com.hederahashgraph.api.proto.java.ClprThrottles;
import org.hiero.mirror.common.domain.clpr.ClprThrottle;
import org.junit.jupiter.api.Test;

class ClprThrottleConverterTest {

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
        final var throttle = ClprThrottleConverter.from(throttles);

        // then
        assertThat(throttle)
                .returns(4_294_967_295L, ClprThrottle::getMaxLocalEndpoints)
                .returns(4_294_967_295L, ClprThrottle::getMaxMessagePayloadBytes)
                .returns(4_294_967_295L, ClprThrottle::getMaxMessagesPerBundle)
                .returns(4_294_967_295L, ClprThrottle::getMaxPeerEndpoints)
                .returns(4_294_967_295L, ClprThrottle::getMaxQueueDepth);
    }

    @Test
    void fromLedgerConfigurationWithoutThrottlesReturnsNull() {
        assertThat(ClprThrottleConverter.from(ClprLedgerConfiguration.getDefaultInstance()))
                .isNull();
    }
}
