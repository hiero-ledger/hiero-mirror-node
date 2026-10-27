// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ClprPendingCommitmentStatus;
import org.junit.jupiter.api.Test;

final class ClprPendingChannelMapperTest {

    private final ClprPendingChannelMapper mapper = new ClprPendingChannelMapperImpl(new CommonMapperImpl());
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Test
    void map() {
        // given
        final var source = domainBuilder.clprChannelPendingCommitment().get();

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getCreatedTimestamp()).isEqualTo(DomainUtils.toTimestamp(source.getCreatedTimestamp()));
        assertThat(actual.getOwnershipCommitment())
                .isEqualTo("0x" + java.util.HexFormat.of().formatHex(source.getOwnershipCommitment()));
        assertThat(actual.getStatus()).isEqualTo(ClprPendingCommitmentStatus.OPEN);
    }
}
