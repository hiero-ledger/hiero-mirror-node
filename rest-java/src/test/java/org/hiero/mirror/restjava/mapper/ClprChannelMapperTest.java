// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.List;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class ClprChannelMapperTest {

    private DomainBuilder domainBuilder;
    private ClprChannelMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ClprChannelMapperImpl(new CommonMapperImpl());
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        // given
        final var endpoint1 = ClprEndpoint.builder()
                .accountId(new byte[] {1})
                .serviceEndpoint(new ClprServiceEndpoint("10.0.0.1", 1))
                .tlsCertificate(new byte[] {2})
                .build();
        final var endpoint2 = ClprEndpoint.builder()
                .accountId(new byte[] {3})
                .serviceEndpoint(new ClprServiceEndpoint("10.0.0.2", 2))
                .tlsCertificate(new byte[] {4})
                .build();
        final var source = domainBuilder
                .clprChannel()
                .customize(c ->
                        c.endpointManifest(List.of(endpoint1, endpoint2)).timestampRange(Range.closedOpen(10L, 20L)))
                .get();

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getCreatedTimestamp()).isEqualTo(DomainUtils.toTimestamp(source.getCreatedTimestamp()));
        assertThat(actual.getLastConfigTimestamp()).isEqualTo(DomainUtils.toTimestamp(source.getLastConfigTimestamp()));
        assertThat(actual.getPeerConfigTimestamp()).isEqualTo(DomainUtils.toTimestamp(source.getPeerConfigTimestamp()));
        assertThat(actual.getTimestamp())
                .returns(DomainUtils.toTimestamp(10L), t -> t.getFrom())
                .returns(DomainUtils.toTimestamp(20L), t -> t.getTo());
        assertThat(actual.getPeerThrottles())
                .returns(15_000_000L, t -> t.getMaxGasPerMessage())
                .returns(39L, t -> t.getMaxLocalEndpoints())
                .returns(8192L, t -> t.getMaxMessagePayloadBytes())
                .returns(100L, t -> t.getMaxMessagesPerBundle())
                .returns(39L, t -> t.getMaxPeerEndpoints())
                .returns(1000L, t -> t.getMaxQueueDepth())
                .returns(4_194_304L, t -> t.getMaxSyncBytes());
        assertThat(actual.getEndpointManifest().getVersion()).isEqualTo(source.getEndpointManifestVersion());
        assertThat(actual.getEndpointManifest().getEndpoints()).hasSize(2);
        assertThat(actual.getEndpointManifest().getEndpoints().get(1).getServiceEndpoint())
                .returns("10.0.0.2", s -> s.getIpAddress())
                .returns(2, s -> s.getPort());
    }

    @Test
    void mapNulls() {
        // given
        final var source = domainBuilder
                .clprChannel()
                .customize(c -> c.endpointManifest(null)
                        .lastConfigTimestamp(null)
                        .peerConfigTimestamp(null)
                        .peerMaxGasPerMessage(null)
                        .signatureScheme(null))
                .get();

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getEndpointManifest().getEndpoints()).isEmpty();
        assertThat(actual.getLastConfigTimestamp()).isNull();
        assertThat(actual.getPeerConfigTimestamp()).isNull();
        assertThat(actual.getPeerThrottles().getMaxGasPerMessage()).isNull();
        assertThat(actual.getSignatureScheme()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ClprSignatureScheme.class)
    void mapSignatureScheme(ClprSignatureScheme scheme) {
        // given
        final var source = domainBuilder
                .clprChannel()
                .customize(c -> c.signatureScheme(scheme))
                .get();

        // when / then
        assertThat(mapper.map(source).getSignatureScheme().getValue()).isEqualTo(scheme.name());
    }
}
