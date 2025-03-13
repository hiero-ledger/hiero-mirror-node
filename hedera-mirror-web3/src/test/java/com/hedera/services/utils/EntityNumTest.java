// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class EntityNumTest {
    @Test
    void overridesJavaLangImpl() {
        final var v = 1_234_567;

        final var subject = EntityNum.fromEntityId(EntityId.of(v));

        assertNotEquals(v, subject.hashCode());
    }

    @Test
    void equalsWorks() {
        final var a = EntityNum.fromEntityId(EntityId.of(1));
        final var b = EntityNum.fromEntityId(EntityId.of(2));
        final var c = a;

        assertNotEquals(a, b);
        assertNotEquals(null, a);
        assertNotEquals(new Object(), a);
        assertEquals(a, c);
    }

    @Test
    void orderingSortsByValue() {
        int value = 100;

        final var base = EntityNum.fromEntityId(EntityId.of(value));
        final var sameButDiff = EntityNum.fromEntityId(EntityId.of(value));
        assertEquals(0, base.compareTo(sameButDiff));
        final var largerNum = EntityNum.fromEntityId(EntityId.of(value + 1));
        assertEquals(-1, base.compareTo(largerNum));
        final var smallerNum = EntityNum.fromEntityId(EntityId.of(value - 1));
        assertEquals(+1, base.compareTo(smallerNum));
    }

    @Test
    void factoriesWorkForInvalidShard() {
        assertEquals(MISSING_NUM, EntityNum.fromAccountId(IdUtils.asAccount("1.0.123")));
        assertEquals(
                EntityNum.fromEntityId(EntityId.of(123)),
                EntityNum.fromEvmAddress(Address.wrap(Bytes.wrap(DomainUtils.toEvmAddress(123)))));
    }
}
