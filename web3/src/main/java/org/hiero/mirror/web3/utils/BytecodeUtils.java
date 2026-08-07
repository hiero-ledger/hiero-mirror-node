// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class for extracting runtime bytecode from init bytecode of a smart contract.
 * <p>
 * Smart contracts have init bytecode (constructor bytecode) and runtime bytecode (the code executed when the contract
 * is called). This class helps in extracting the runtime bytecode from the given init bytecode by searching for
 * specific patterns.
 * </p>
 */
@UtilityClass
public class BytecodeUtils {

    public static final String SKIP_INIT_CODE_CHECK = "HIERO_MIRROR_WEB3_EVM_SKIPINITCODECHECK";
    private static final String CODECOPY = "39";
    private static final String FREE_MEMORY_POINTER = "60806040";
    private static final String FREE_MEMORY_POINTER_2 = "60606040";
    private static final String RETURN = "f3";
    private static final String RUNTIME_CODE_PREFIX =
            "6080"; // The pattern to find the start of the runtime code in the init bytecode

    /**
     * The shortest string that can satisfy {@link #isInitBytecode(String)}: an eight character free memory pointer
     * setup, one filler character, CODECOPY, one filler character and RETURN.
     */
    private static final int MINIMUM_INIT_CODE_SIZE =
            FREE_MEMORY_POINTER.length() + 1 + CODECOPY.length() + 1 + RETURN.length();

    public static String extractRuntimeBytecode(String initBytecode) {
        // Check if the bytecode starts with "0x" and remove it if necessary
        if (initBytecode.startsWith(HEX_PREFIX)) {
            initBytecode = initBytecode.substring(HEX_PREFIX.length());
        }

        String runtimeBytecode = getRuntimeBytecode(initBytecode);

        return HEX_PREFIX + runtimeBytecode; // Append "0x" prefix and return
    }

    @NonNull
    private static String getRuntimeBytecode(final String initBytecode) {
        // Find the first occurrence of "CODECOPY" (39)
        int codeCopyIndex = initBytecode.indexOf(CODECOPY);

        if (codeCopyIndex == -1) {
            throw new IllegalArgumentException("CODECOPY instruction (39) not found in init bytecode.");
        }

        // Find the first occurrence of "6080" after the "CODECOPY"
        int runtimeCodePrefixIndex = initBytecode.indexOf(RUNTIME_CODE_PREFIX, codeCopyIndex);

        if (runtimeCodePrefixIndex == -1) {
            throw new IllegalArgumentException("Runtime code prefix (6080) not found after CODECOPY.");
        }

        // Extract the runtime bytecode starting from the runtimeCodePrefixIndex
        return initBytecode.substring(runtimeCodePrefixIndex);
    }

    /**
     * Checks if a given data string is likely init bytecode, by looking for a free memory pointer setup followed by a
     * CODECOPY and then a RETURN, with at least one character separating each.
     * Selecting the earliest occurrence of each marker is equivalent to the regex: an earlier match only widens the
     * window available to the markers that follow it, so if any combination satisfies the pattern the greedy leftmost
     * combination does too.
     *
     * @param data the data string to check, with or without a {@code 0x} prefix.
     * @return true if it is init bytecode, false otherwise.
     */
    public static boolean isInitBytecode(@Nullable final String data) {
        if (data == null) {
            return false;
        }

        final int start = data.startsWith(HEX_PREFIX) ? HEX_PREFIX.length() : 0;
        if (data.length() - start < MINIMUM_INIT_CODE_SIZE || !isHex(data, start)) {
            return false;
        }

        final int pointer = indexOfFreeMemoryPointer(data, start);
        if (pointer < 0) {
            return false;
        }

        // CODECOPY, separated from the free memory pointer setup by at least one character
        final int codeCopy = data.indexOf(CODECOPY, pointer + FREE_MEMORY_POINTER.length() + 1);
        if (codeCopy < 0) {
            return false;
        }

        // RETURN, separated from CODECOPY by at least one character
        return indexOfReturn(data, codeCopy + CODECOPY.length() + 1) >= 0;
    }

    public static boolean isValidInitBytecode(final String data) {
        return shouldSkipBytecodeCheck() || BytecodeUtils.isInitBytecode(data);
    }

    private static boolean shouldSkipBytecodeCheck() {
        return Boolean.parseBoolean(System.getenv(SKIP_INIT_CODE_CHECK));
    }

    /**
     * Returns the first index at or after {@code from} of either free memory pointer setup sequence. Both sequences are
     * numeric, so no case folding is required.
     */
    private static int indexOfFreeMemoryPointer(final String data, final int from) {
        final int first = data.indexOf(FREE_MEMORY_POINTER, from);
        final int second = data.indexOf(FREE_MEMORY_POINTER_2, from);

        if (first < 0) {
            return second;
        }
        return second < 0 ? first : Math.min(first, second);
    }

    /**
     * Returns the first index at or after {@code from} of the RETURN opcode, matched case insensitively.
     */
    private static int indexOfReturn(final String data, final int from) {
        for (int i = Math.max(from, 0); i < data.length() - 1; i++) {
            final char c = data.charAt(i);
            if ((c == 'f' || c == 'F') && data.charAt(i + 1) == '3') {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns whether every character from {@code start} onwards is an ASCII hexadecimal digit.
     */
    private static boolean isHex(final String data, final int start) {
        for (int i = start; i < data.length(); i++) {
            final char c = data.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                return false;
            }
        }

        return true;
    }
}
