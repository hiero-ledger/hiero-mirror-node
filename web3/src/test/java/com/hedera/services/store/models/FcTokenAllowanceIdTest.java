// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.utils.EntityNum;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FcTokenAllowanceIdTest {

    private final EntityNum tokenNum = EntityNum.fromEntityId(EntityId.of(1));
    private final EntityNum spenderNum = EntityNum.fromEntityId(EntityId.of(2));

    private FcTokenAllowanceId subject;

    @BeforeEach
    void setup() {
        subject = FcTokenAllowanceId.from(tokenNum, spenderNum);
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two =
                FcTokenAllowanceId.from(EntityNum.fromEntityId(EntityId.of(3)), EntityNum.fromEntityId(EntityId.of(4)));
        final var three =
                FcTokenAllowanceId.from(EntityNum.fromEntityId(EntityId.of(1)), EntityNum.fromEntityId(EntityId.of(2)));

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
    void orderingPrioritizesTokenNumThenSpender() {
        final var base = new FcTokenAllowanceId(tokenNum, spenderNum);
        final var sameButDiff = base;
        assertEquals(0, base.compareTo(sameButDiff));
        final var largerNum =
                new FcTokenAllowanceId(EntityNum.fromEntityId(EntityId.of(3)), EntityNum.fromEntityId(EntityId.of(1)));
        assertEquals(-1, base.compareTo(largerNum));
        final var smallerKey =
                new FcTokenAllowanceId(EntityNum.fromEntityId(EntityId.of(0)), EntityNum.fromEntityId(EntityId.of(1)));
        assertEquals(+1, base.compareTo(smallerKey));
    }
}
