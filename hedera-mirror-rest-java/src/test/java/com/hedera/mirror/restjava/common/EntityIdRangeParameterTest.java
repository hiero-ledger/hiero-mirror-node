// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.restjava.RestJavaProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdRangeParameterTest {

    @Mock
    private RestJavaProperties properties;

    private MockedStatic<SpringApplicationContext> context;

    @BeforeEach
    void setUp() {
        context = Mockito.mockStatic(SpringApplicationContext.class);
        when(SpringApplicationContext.getBean(RestJavaProperties.class)).thenReturn(properties);
    }

    @AfterEach
    void closeMocks() {
        context.close();
    }

    @Test
    void testConversion() {
        assertThat(new EntityIdRangeParameter(RangeOperator.GTE, EntityId.of("0.0.2000")))
                .isEqualTo(EntityIdRangeParameter.valueOf("gte:0.0.2000"));
        assertThat(new EntityIdRangeParameter(RangeOperator.EQ, EntityId.of("0.0.2000")))
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
        assertThat(new EntityIdRangeParameter(RangeOperator.EQ, entityId))
                .isEqualTo(EntityIdRangeParameter.valueOf(input));
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
    @ValueSource(strings = {"0.0.4294967296", "32768.65536.4294967296", "100000.65535.000000001"})
    @DisplayName("EntityIdRangeParameter parse from string tests, negative cases for ID having valid format")
    void testInvalidEntity(String input) {
        assertThrows(InvalidEntityException.class, () -> EntityIdRangeParameter.valueOf(input));
    }
}
