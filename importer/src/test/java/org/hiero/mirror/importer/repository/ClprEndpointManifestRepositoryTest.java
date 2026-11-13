// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprEndpoint;
import org.hiero.mirror.common.domain.clpr.ClprEndpointManifest;
import org.hiero.mirror.common.domain.clpr.ClprServiceEndpoint;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprEndpointManifestRepositoryTest extends ImporterIntegrationTest {

    private final ClprEndpointManifestRepository clprEndpointManifestRepository;

    @Test
    void save() {
        var clprEndpointManifest = domainBuilder.clprEndpointManifest().persist();
        assertThat(clprEndpointManifestRepository.findById(clprEndpointManifest.getConsensusTimestamp()))
                .get()
                .isEqualTo(clprEndpointManifest);
    }

    // The endpoints jsonb column holds a list of ClprEndpoint, each nesting a ClprServiceEndpoint. Verify the
    // nested->nested structure round-trips through a real database read rather than only an in-memory comparison.
    @Test
    void saveWithNestedEndpoints() {
        var endpoints = List.of(
                ClprEndpoint.builder()
                        .accountId(domainBuilder.bytes(20))
                        .serviceEndpoint(ClprServiceEndpoint.builder()
                                .ipAddress("10.0.0.1")
                                .port(50211)
                                .build())
                        .tlsCertificate(domainBuilder.bytes(256))
                        .build(),
                ClprEndpoint.builder()
                        .accountId(domainBuilder.bytes(20))
                        .serviceEndpoint(ClprServiceEndpoint.builder()
                                .ipAddress("10.0.0.2")
                                .port(50212)
                                .build())
                        .tlsCertificate(domainBuilder.bytes(256))
                        .build());
        var clprEndpointManifest = domainBuilder
                .clprEndpointManifest()
                .customize(b -> b.endpoints(endpoints))
                .persist();

        var actual = clprEndpointManifestRepository.findById(clprEndpointManifest.getConsensusTimestamp());

        assertThat(actual).get().extracting(ClprEndpointManifest::getEndpoints).isEqualTo(endpoints);
    }

    @Test
    void findLatest() {
        domainBuilder.clprEndpointManifest().persist();
        var latest = domainBuilder.clprEndpointManifest().persist();
        assertThat(clprEndpointManifestRepository.findLatest()).get().isEqualTo(latest);
    }

    @Test
    void findLatestEmpty() {
        assertThat(clprEndpointManifestRepository.findLatest()).isEmpty();
    }
}
