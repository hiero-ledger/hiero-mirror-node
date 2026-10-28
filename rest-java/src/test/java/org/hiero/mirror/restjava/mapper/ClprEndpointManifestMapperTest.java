// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;

final class ClprEndpointManifestMapperTest {

    private final ClprEndpointManifestMapper mapper = new ClprEndpointManifestMapperImpl(new CommonMapperImpl());
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Test
    void map() {
        // given
        final var source = domainBuilder.clprEndpointManifest().get();

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getConsensusTimestamp()).isEqualTo(DomainUtils.toTimestamp(source.getConsensusTimestamp()));
        assertThat(actual.getVersion()).isEqualTo(source.getVersion());
        // The 20 byte service address isn't padded
        assertThat(actual.getServiceAddress()).isEqualTo("0x" + HexFormat.of().formatHex(source.getServiceAddress()));
        assertThat(actual.getEndpoints()).singleElement().satisfies(e -> {
            assertThat(e.getServiceEndpoint().getIpAddress()).isEqualTo("10.0.0.5");
            assertThat(e.getServiceEndpoint().getPort()).isEqualTo(50211);
        });
    }
}
