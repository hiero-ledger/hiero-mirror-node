// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.utils.EntityNum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FcTokenAllowanceIdTest {
    private final EntityNum tokenNum = EntityNum.fromLong(1L);
    private final EntityNum spenderNum = EntityNum.fromLong(2L);

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
                "FcTokenAllowanceId{tokenNum=" + tokenNum + ", spenderNum=" + spenderNum + "}", subject.toString());
    }

    @Test
    void gettersWork() {
        assertEquals(1L, subject.getTokenNum().getId());
        assertEquals(2L, subject.getSpenderNum().getId());
    }

    @Test
    void orderingPrioritizesTokenNumThenSpender() {
        final var base = new FcTokenAllowanceId(tokenNum, spenderNum);
        final var sameButDiff = base;
        assertEquals(0, base.compareTo(sameButDiff));
        final var largerNum = new FcTokenAllowanceId(
                EntityNum.fromLong(tokenNum.getId() + 1), EntityNum.fromLong(spenderNum.getId() - 1));
        assertEquals(-1, base.compareTo(largerNum));
        final var smallerKey = new FcTokenAllowanceId(
                EntityNum.fromLong(tokenNum.getId() - 1), EntityNum.fromLong(spenderNum.getId() - 1));
        assertEquals(+1, base.compareTo(smallerKey));
    }
}
