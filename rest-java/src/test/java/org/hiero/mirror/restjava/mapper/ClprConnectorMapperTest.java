// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.clpr.ClprSignatureScheme;
import org.hiero.mirror.common.util.DomainUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class ClprConnectorMapperTest {

    private DomainBuilder domainBuilder;
    private ClprConnectorMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ClprConnectorMapperImpl(new CommonMapperImpl());
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        // given
        final var source = domainBuilder
                .clprConnector()
                .customize(c -> c.lockedStake(5_000_000_000L).timestampRange(Range.closedOpen(10L, 20L)))
                .get();

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getCreatedTimestamp()).isEqualTo(DomainUtils.toTimestamp(source.getCreatedTimestamp()));
        // A plain number, not an entity id like 0.0.5000000000
        assertThat(actual.getLockedStake()).isEqualTo("5000000000");
        assertThat(actual.getTimestamp())
                .returns(DomainUtils.toTimestamp(10L), t -> t.getFrom())
                .returns(DomainUtils.toTimestamp(20L), t -> t.getTo());
    }

    @Test
    void mapNulls() {
        // given
        final var source = domainBuilder
                .clprConnector()
                .customize(c -> c.deleted(null).signatureScheme(null))
                .get();

        // when
        final var actual = mapper.map(source);

        // then
        assertThat(actual.getDeleted()).isNull();
        assertThat(actual.getSignatureScheme()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ClprSignatureScheme.class)
    void mapSignatureScheme(ClprSignatureScheme scheme) {
        // given
        final var source = domainBuilder
                .clprConnector()
                .customize(c -> c.signatureScheme(scheme))
                .get();

        // when / then
        assertThat(mapper.map(source).getSignatureScheme().getValue()).isEqualTo(scheme.name());
    }
}
