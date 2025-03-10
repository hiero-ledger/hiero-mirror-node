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

        final var subject = new EntityNum(v);

        assertNotEquals(v, subject.hashCode());
    }

    @Test
    void equalsWorks() {
        final var a = new EntityNum(1);
        final var b = new EntityNum(2);
        final var c = a;

        assertNotEquals(a, b);
        assertNotEquals(null, a);
        assertNotEquals(new Object(), a);
        assertEquals(a, c);
    }

    @Test
    void factoriesWorkForValidShardRealm() {
        final var expected = EntityNum.fromInt(123);

        assertEquals(expected, EntityNum.fromLong(123L));
    }

    @Test
    void canGetId() {
        final long realNum = EntityId.of(Long.MAX_VALUE).getId();

        final var subject = EntityNum.fromLong(realNum);

        assertEquals(realNum, subject.getId());
    }

    @Test
    void orderingSortsByValue() {
        int value = 100;

        final var base = new EntityNum(value);
        final var sameButDiff = EntityNum.fromInt(value);
        assertEquals(0, base.compareTo(sameButDiff));
        final var largerNum = new EntityNum(value + 1);
        assertEquals(-1, base.compareTo(largerNum));
        final var smallerNum = new EntityNum(value - 1);
        assertEquals(+1, base.compareTo(smallerNum));
    }

    @Test
    void factoriesWorkForInvalidShard() {
        assertEquals(MISSING_NUM, EntityNum.fromAccountId(IdUtils.asAccount("1.0.123")));
        assertEquals(
                new EntityNum(123), EntityNum.fromEvmAddress(Address.wrap(Bytes.wrap(DomainUtils.toEvmAddress(123)))));
    }
}
