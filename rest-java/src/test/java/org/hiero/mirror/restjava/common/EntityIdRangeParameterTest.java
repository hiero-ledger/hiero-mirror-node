// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.restjava.common.RangeOperator.EQ;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.exception.InvalidEntityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdRangeParameterTest {

    private static final DomainBuilder domainBuilder = new DomainBuilder();

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

    @TestFactory
    Stream<DynamicTest> testValidParam() {
        final var entityId = domainBuilder.entityNum(1000);
        final var inputs = List.of(
                entityId.toString(),
                String.format("%d.%d", entityId.getRealm(), entityId.getNum()),
                Long.toString(entityId.getNum()));
        final ThrowingConsumer<String> executor = input -> {
            assertThat(EntityIdRangeParameter.valueOf(input)).isEqualTo(new EntityIdRangeParameter(EQ, entityId));
        };

        return DynamicTest.stream(inputs.iterator(), Function.identity(), executor);
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
