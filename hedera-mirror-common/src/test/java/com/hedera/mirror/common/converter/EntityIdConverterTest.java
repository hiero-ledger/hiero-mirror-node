// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.converter;

import static com.hedera.mirror.common.converter.EntityIdConverter.INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class EntityIdConverterTest {

    @Test
    void testToDatabaseColumn() {
        Assertions.assertThat(INSTANCE.convertToDatabaseColumn(null)).isNull();
        Assertions.assertThat(INSTANCE.convertToDatabaseColumn(EntityId.of(10L, 10L, 10L)))
                .isEqualTo(2814792716779530L);
    }

    @Test
    void testToEntityAttribute() {
        assertThat(INSTANCE.convertToEntityAttribute(null)).isNull();
        assertThat(INSTANCE.convertToEntityAttribute(9223372036854775807L))
                .isEqualTo(EntityId.of(32767L, 65535L, 4294967295L));
    }
}
