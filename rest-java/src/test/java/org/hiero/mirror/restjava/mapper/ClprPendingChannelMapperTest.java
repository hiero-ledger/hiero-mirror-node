// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.util.List;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.rest.model.ClprChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ClprPendingChannelMapperTest {

    private ClprPendingChannelMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ClprPendingChannelMapperImpl(new CommonMapperImpl());
    }

    @Test
    void map() {
        // given
        final var pending = ClprChannelPendingCommitment.builder()
                .createdTimestamp(1_000_000_000L)
                .ownershipCommitment(new byte[] {0x7a, (byte) 0xb3})
                .status(org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus.OPEN)
                .timestampRange(Range.atLeast(1_000_000_000L))
                .build();

        // when
        final var actual = mapper.map(pending);

        // then
        assertThat(actual.getCreatedTimestamp()).isEqualTo("1.000000000");
        assertThat(actual.getOwnershipCommitment()).isEqualTo("0x7ab3");
        assertThat(actual.getStatus()).isEqualTo(org.hiero.mirror.rest.model.ClprChannelStatus.PENDING);
        assertThat(actual.getChainId()).isNull();
        assertThat(actual.getChannelId()).isNull();
        assertThat(actual.getTimestamp()).isNull();
    }

    @Test
    void mapCollection() {
        // given
        final var first = ClprChannelPendingCommitment.builder()
                .ownershipCommitment(new byte[] {0x01})
                .build();
        final var second = ClprChannelPendingCommitment.builder()
                .ownershipCommitment(new byte[] {0x02})
                .build();

        // when / then
        assertThat(mapper.map(List.of(first, second)))
                .extracting(ClprChannel::getOwnershipCommitment)
                .containsExactly("0x01", "0x02");
        assertThat(mapper.map(List.of())).isEmpty();
    }
}
