// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class BitPackUtils {

    public static final long MAX_NUM_ALLOWED = 0xFFFFFFFFL;
    private static final long MASK_INT_AS_UNSIGNED_LONG = (1L << 32) - 1;

    /**
     * Returns the positive long represented by the given integer code.
     *
     * @param code an int to interpret as unsigned
     * @return the corresponding positive long
     */
    public static long numFromCode(int code) {
        return code & MASK_INT_AS_UNSIGNED_LONG;
    }

    /**
     * Returns the int representing the given positive long.
     *
     * @param num a positive long
     * @return the corresponding integer code
     */
    public static int codeFromNum(long num) {
        assertValid(num);
        return (int) num;
    }

    /**
     * Throws an exception if the given long is not a number in the allowed range.
     *
     * @param num the long to check
     * @throws IllegalArgumentException if the argument is less than 0 or greater than 4_294_967_295
     */
    public static void assertValid(long num) {
        if (num < 0 || num > MAX_NUM_ALLOWED) {
            throw new IllegalArgumentException("Serial number " + num + " out of range!");
        }
    }

    /**
     * Checks if the given long is not a number in the allowed range
     *
     * @param num given long number to check
     * @return true if valid, else returns false
     */
    public static boolean isValidNum(long num) {
        return num >= 0 && num <= MAX_NUM_ALLOWED;
    }
}
