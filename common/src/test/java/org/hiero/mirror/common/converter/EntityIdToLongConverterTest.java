// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;

final class EntityIdToLongConverterTest {

    private final EntityIdToLongConverter converter = new EntityIdToLongConverter();

    @Test
    void convert() {
        assertThat(converter.convert(null)).isNull();
        assertThat(converter.convert(EntityId.EMPTY)).isNull();
        assertThat(converter.convert(EntityId.ZERO)).isZero();
        assertThat(converter.convert(EntityId.of(10L, 10L, 10L))).isEqualTo(180146733873889290L);
    }
}
