// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprLedgerConfiguration;
import org.hiero.mirror.common.domain.clpr.ClprThrottle;
import org.hiero.mirror.rest.model.ClprPeerThrottles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ClprLedgerConfigurationMapperTest {

    private ClprLedgerConfigurationMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ClprLedgerConfigurationMapperImpl(new CommonMapperImpl());
    }

    @Test
    void map() {
        // given
        final var configuration = ClprLedgerConfiguration.builder()
                .chainId("eip155:1")
                .consensusTimestamp(1726874345_123456789L)
                .initialTrustAnchor(new byte[] {0x33, 0x44})
                .initialTrustAnchorId(new byte[] {(byte) 0x9f, (byte) 0x8e})
                .protocolVersion(1L)
                .serviceAddress(new byte[] {0x01, 0x6e})
                .throttle(ClprThrottle.builder()
                        .maxGasPerMessage(15_000_000L)
                        .maxLocalEndpoints(39L)
                        .maxMessagePayloadBytes(65_536L)
                        .maxMessagesPerBundle(1000L)
                        .maxPeerEndpoints(38L)
                        .maxQueueDepth(10_000L)
                        .maxSyncBytes(4_194_304L)
                        .build())
                .build();

        // when
        final var actual = mapper.map(configuration);

        // then
        assertThat(actual.getChainId()).isEqualTo("eip155:1");
        assertThat(actual.getConsensusTimestamp()).isEqualTo("1726874345.123456789");
        assertThat(actual.getInitialTrustAnchor()).isEqualTo("0x3344");
        assertThat(actual.getInitialTrustAnchorId()).isEqualTo("0x9f8e");
        assertThat(actual.getProtocolVersion()).isEqualTo(1L);
        assertThat(actual.getServiceAddress()).isEqualTo("0x016e");
        assertThat(actual.getThrottles())
                .isEqualTo(new ClprPeerThrottles()
                        .maxGasPerMessage(15_000_000L)
                        .maxLocalEndpoints(39L)
                        .maxMessagePayloadBytes(65_536L)
                        .maxMessagesPerBundle(1000L)
                        .maxPeerEndpoints(38L)
                        .maxQueueDepth(10_000L)
                        .maxSyncBytes(4_194_304L));
    }

    @Test
    void mapCollection() {
        // given
        final var first = ClprLedgerConfiguration.builder()
                .consensusTimestamp(1_000_000_000L)
                .build();
        final var second = ClprLedgerConfiguration.builder()
                .consensusTimestamp(2_000_000_000L)
                .build();

        // when / then
        assertThat(mapper.map(List.of(first, second)))
                .extracting(c -> c.getConsensusTimestamp())
                .containsExactly("1.000000000", "2.000000000");
        assertThat(mapper.map(List.<ClprLedgerConfiguration>of())).isEmpty();
    }

    @Test
    void mapNullBytesAndThrottles() {
        // given
        final var configuration = ClprLedgerConfiguration.builder()
                .consensusTimestamp(1_000_000_000L)
                .protocolVersion(2L)
                .build();

        // when
        final var actual = mapper.map(configuration);

        // then
        assertThat(actual.getChainId()).isNull();
        assertThat(actual.getConsensusTimestamp()).isEqualTo("1.000000000");
        assertThat(actual.getInitialTrustAnchor()).isNull();
        assertThat(actual.getInitialTrustAnchorId()).isNull();
        assertThat(actual.getProtocolVersion()).isEqualTo(2L);
        assertThat(actual.getServiceAddress()).isNull();
        assertThat(actual.getThrottles()).isEqualTo(new ClprPeerThrottles());
    }
}
