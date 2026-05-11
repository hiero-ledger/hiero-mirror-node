// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import org.assertj.core.api.Assertions;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.Test;

class EntityIdConverterTest {

    private final EntityIdConverter.Writer writer = new EntityIdConverter.Writer();
    private final EntityIdConverter.Reader reader = new EntityIdConverter.Reader();

    @Test
    void testToDatabaseColumn() {
        Assertions.assertThat(writer.convert(null)).isNull();
        Assertions.assertThat(writer.convert(EntityId.of(10L, 10L, 10L))).isEqualTo(180146733873889290L);
    }

    @Test
    void testToEntityAttribute() {
        Assertions.assertThat(reader.convert(null)).isNull();
        Assertions.assertThat(reader.convert(-1L)).isEqualTo(EntityId.of(1023L, 65535L, 274877906943L));
    }
}
