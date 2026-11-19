// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ClprEndpointManifestMapperTest {

    private ClprEndpointManifestMapper mapper;

    @BeforeEach
    void setup() {
        final var commonMapper = new CommonMapperImpl();
        mapper = new ClprEndpointManifestMapperImpl(new ClprChannelMapperImpl(commonMapper), commonMapper);
    }

    @Test
    void map() {
        // given
        final var manifest = ClprEndpointManifest.builder()
                .consensusTimestamp(1726874345_123456789L)
                .endpoints(List.of(
                        ClprEndpoint.builder()
                                .accountId(new byte[] {0x01})
                                .serviceEndpoint(new ClprServiceEndpoint("10.0.0.5", 50211))
                                .tlsCertificate(new byte[] {0x02, 0x03})
                                .build(),
                        ClprEndpoint.builder()
                                .serviceEndpoint(new ClprServiceEndpoint("10.0.0.6", 50212))
                                .build()))
                .serviceAddress(new byte[] {0x01, 0x6e})
                .version(3L)
                .build();

        // when
        final var actual = mapper.map(manifest);

        // then
        assertThat(actual.getConsensusTimestamp()).isEqualTo("1726874345.123456789");
        assertThat(actual.getServiceAddress()).isEqualTo("0x016e");
        assertThat(actual.getVersion()).isEqualTo(3L);
        assertThat(actual.getEndpoints())
                .containsExactly(
                        new org.hiero.mirror.rest.model.ClprEndpoint()
                                .accountId("0x01")
                                .serviceEndpoint(new org.hiero.mirror.rest.model.ClprServiceEndpoint()
                                        .ipAddress("10.0.0.5")
                                        .port(50211))
                                .tlsCertificate("0x0203"),
                        new org.hiero.mirror.rest.model.ClprEndpoint()
                                .serviceEndpoint(new org.hiero.mirror.rest.model.ClprServiceEndpoint()
                                        .ipAddress("10.0.0.6")
                                        .port(50212)));
    }

    @Test
    void mapEmptyEndpoints() {
        // given
        final var manifest = ClprEndpointManifest.builder()
                .consensusTimestamp(1_000_000_000L)
                .endpoints(List.of())
                .version(1L)
                .build();

        // when
        final var actual = mapper.map(manifest);

        // then
        assertThat(actual.getEndpoints()).isEmpty();
        assertThat(actual.getServiceAddress()).isNull();
    }

    @Test
    void mapEndpointWithoutServiceEndpoint() {
        // given
        final var manifest = ClprEndpointManifest.builder()
                .consensusTimestamp(1_000_000_000L)
                .endpoints(List.of(
                        ClprEndpoint.builder().accountId(new byte[] {0x01}).build()))
                .version(1L)
                .build();

        // when
        final var actual = mapper.map(manifest);

        // then
        assertThat(actual.getEndpoints())
                .containsExactly(new org.hiero.mirror.rest.model.ClprEndpoint().accountId("0x01"));
    }
}
