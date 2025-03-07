// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import static com.hedera.mirror.restjava.common.RangeOperator.EQ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdRangeParameterTest {

    private final CommonProperties commonProperties = new CommonProperties();

    @BeforeEach
    void setup() {
        EntityIdParameter.PROPERTIES.set(commonProperties);
    }

    @Test
    void testConversion() {
        assertThat(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of("0.0.2000")))
                .isEqualTo(EntityIdRangeParameter.valueOf("gte:0.0.2000"));
        assertThat(new EntityIdRangeParameter(EQ, EntityId.of("0.0.2000")))
                .isEqualTo(EntityIdRangeParameter.valueOf("0.0.2000"));
        assertThat(EntityIdRangeParameter.EMPTY)
                .isEqualTo(EntityIdRangeParameter.valueOf(""))
                .isEqualTo(EntityIdRangeParameter.valueOf(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.2", "0.2", "2"})
    @DisplayName("EntityIdRangeParameter parse from string tests, valid cases")
    void testValidParam(String input) {
        var entityId = EntityId.of(0, 0, 2);
        assertThat(new EntityIdRangeParameter(EQ, entityId)).isEqualTo(EntityIdRangeParameter.valueOf(input));
    }

    @Test
    void nonDefaultShardRealm() {
        commonProperties.setRealm(1000L);
        commonProperties.setShard(1);
        var id = EntityId.of(commonProperties.getShard(), commonProperties.getRealm(), 1);
        assertThat(EntityIdRangeParameter.valueOf("1")).isEqualTo(new EntityIdRangeParameter(EQ, id));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0.1.x",
                "0.1.2.3",
                "a",
                "a.b.c",
                "-1",
                "-1.-1",
                "-1.-1.-1",
                "0 . 0.1 ",
                "0..1",
                ".1",
                "0.0.-1",
                "eq:0.0.1:someinvalidstring",
                "BLAH:0.0.1",
                "0.0.1:someinvalidstring"
            })
    @DisplayName("EntityIdRangeParameter parse from string tests, negative cases")
    void testInvalidParam(String input) {
        assertThrows(IllegalArgumentException.class, () -> EntityIdRangeParameter.valueOf(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.180146733873889291", "1024.65536.180146733873889291", "10000.65535.000000001"})
    @DisplayName("EntityIdRangeParameter parse from string tests, negative cases for ID having valid format")
    void testInvalidEntity(String input) {
        assertThrows(InvalidEntityException.class, () -> EntityIdRangeParameter.valueOf(input));
    }
}
