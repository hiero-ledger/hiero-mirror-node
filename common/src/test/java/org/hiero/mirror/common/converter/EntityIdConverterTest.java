// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.converter.EntityIdConverter.INSTANCE;

import org.assertj.core.api.Assertions;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;

class EntityIdConverterTest {

    @Test
    void testToDatabaseColumn() {
        Assertions.assertThat(INSTANCE.convertToDatabaseColumn(null)).isNull();
        Assertions.assertThat(INSTANCE.convertToDatabaseColumn(EntityId.of(10L, 10L, 10L)))
                .isEqualTo(180146733873889290L);
    }

    @Test
    void testToEntityAttribute() {
        assertThat(INSTANCE.convertToEntityAttribute(null)).isNull();
        assertThat(INSTANCE.convertToEntityAttribute(-1L)).isEqualTo(EntityId.of(1023L, 65535L, 274877906943L));
    }
}
