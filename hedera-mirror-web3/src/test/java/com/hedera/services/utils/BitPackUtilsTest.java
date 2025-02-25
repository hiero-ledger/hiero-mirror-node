// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import static com.hedera.services.utils.BitPackUtils.MAX_NUM_ALLOWED;
import static com.hedera.services.utils.BitPackUtils.isValidNum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BitPackUtilsTest {

    @Test
    void validateLong() {
        assertFalse(isValidNum(MAX_NUM_ALLOWED + 10));
        assertTrue(isValidNum(MAX_NUM_ALLOWED));
        assertTrue(isValidNum(0L));
        assertFalse(isValidNum(-1L));
    }

    @Test
    void numFromCodeWorks() {
        // expect:
        assertEquals(MAX_NUM_ALLOWED, BitPackUtils.numFromCode((int) MAX_NUM_ALLOWED));
    }

    @Test
    void codeFromNumWorks() {
        // expect:
        assertEquals((int) MAX_NUM_ALLOWED, BitPackUtils.codeFromNum(MAX_NUM_ALLOWED));
    }

    @Test
    void codeFromNumThrowsWhenOutOfRange() {
        // expect:
        assertThrows(IllegalArgumentException.class, () -> BitPackUtils.codeFromNum(-1));
        assertThrows(IllegalArgumentException.class, () -> BitPackUtils.codeFromNum(MAX_NUM_ALLOWED + 1));
    }
}
