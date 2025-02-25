// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.utils.EntityNum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FcTokenAllowanceIdTest {
    private EntityNum tokenNum = EntityNum.fromLong(1L);
    private EntityNum spenderNum = EntityNum.fromLong(2L);

    private FcTokenAllowanceId subject;

    @BeforeEach
    void setup() {
        subject = FcTokenAllowanceId.from(tokenNum, spenderNum);
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two = FcTokenAllowanceId.from(EntityNum.fromLong(3L), EntityNum.fromLong(4L));
        final var three = FcTokenAllowanceId.from(EntityNum.fromLong(1L), EntityNum.fromLong(2L));

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(two, one);
        assertEquals(one, three);

        assertEquals(one.hashCode(), three.hashCode());
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "FcTokenAllowanceId{tokenNum=" + tokenNum.longValue() + ", spenderNum=" + spenderNum.longValue() + "}",
                subject.toString());
    }

    @Test
    void gettersWork() {
        assertEquals(1L, subject.getTokenNum().longValue());
        assertEquals(2L, subject.getSpenderNum().longValue());
    }

    @Test
    void orderingPrioritizesTokenNumThenSpender() {
        final var base = new FcTokenAllowanceId(tokenNum, spenderNum);
        final var sameButDiff = base;
        assertEquals(0, base.compareTo(sameButDiff));
        final var largerNum = new FcTokenAllowanceId(
                EntityNum.fromInt(tokenNum.intValue() + 1), EntityNum.fromInt(spenderNum.intValue() - 1));
        assertEquals(-1, base.compareTo(largerNum));
        final var smallerKey = new FcTokenAllowanceId(
                EntityNum.fromInt(tokenNum.intValue() - 1), EntityNum.fromInt(spenderNum.intValue() - 1));
        assertEquals(+1, base.compareTo(smallerKey));
    }
}
