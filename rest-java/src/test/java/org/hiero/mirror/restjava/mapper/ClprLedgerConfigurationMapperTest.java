// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.Test;

final class ClprLedgerConfigurationMapperTest {

    private final ClprLedgerConfigurationMapper mapper = new ClprLedgerConfigurationMapperImpl(new CommonMapperImpl());
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Test
    void map() {
        // given
        final var source = domainBuilder.clprLedgerConfiguration().get();

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getChainId()).isEqualTo(source.getChainId());
        assertThat(actual.getConsensusTimestamp()).isEqualTo(DomainUtils.toTimestamp(source.getConsensusTimestamp()));
        assertThat(actual.getProtocolVersion()).isEqualTo(source.getProtocolVersion());
        // The 20 byte service address isn't padded
        assertThat(actual.getServiceAddress()).isEqualTo("0x" + HexFormat.of().formatHex(source.getServiceAddress()));
        assertThat(actual.getThrottles())
                .returns(15_000_000L, t -> t.getMaxGasPerMessage())
                .returns(39L, t -> t.getMaxLocalEndpoints())
                .returns(65_536L, t -> t.getMaxMessagePayloadBytes())
                .returns(1000L, t -> t.getMaxMessagesPerBundle())
                .returns(39L, t -> t.getMaxPeerEndpoints())
                .returns(10_000L, t -> t.getMaxQueueDepth())
                .returns(4_194_304L, t -> t.getMaxSyncBytes());
    }
}
