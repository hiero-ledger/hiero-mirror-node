// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NonNull;

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
    private static final String RETURN = "f3";
    private static final long MINIMUM_INIT_CODE_SIZE = 14L;
    private static final String FREE_MEMORY_POINTER = "60806040";
    private static final String FREE_MEMORY_POINTER_2 = "60606040";
    private static final String RUNTIME_CODE_PREFIX =
            "6080"; // The pattern to find the start of the runtime code in the init bytecode

    /**
     * Compiled regex pattern to match the init bytecode sequence. The pattern checks for a sequence of a free memory
     * pointer setup, a CODECOPY operation, and a RETURN operation, in that order. The sequence is matched
     * case-insensitively to account for hexadecimal representations.
     * <p>
     * Pattern explanation: - (%s|%s) matches either FREE_MEMORY_POINTER or FREE_MEMORY_POINTER_2, which represent setup
     * instructions for the free memory pointer, required for initialization bytecode. - [0-9a-z]+ matches one or more
     * valid hexadecimal characters (0-9, a-f) after the free memory pointer setup. - %s matches the CODECOPY opcode,
     * which copies code to memory and typically follows the memory pointer setup in init bytecode. - [0-9a-z]+ matches
     * one or more valid hexadecimal characters (0-9, a-f) between CODECOPY and RETURN. - %s matches the RETURN opcode,
     * signaling the end of the initialization bytecode.
     *
     * <p>
     * Example pattern: (60806040|60606040)[0-9a-z]+39[0-9a-z]+f3 This example would match any sequence where either
     * "60806040" or "60606040" appears, followed by "39" (CODECOPY) and then "f3" (RETURN), with valid hexadecimal
     * characters in between.
     */
    private static final Pattern INIT_BYTECODE_PATTERN = Pattern.compile(
            String.format(
                    "(%s|%s)[0-9a-z]+%s[0-9a-z]+%s", FREE_MEMORY_POINTER, FREE_MEMORY_POINTER_2, CODECOPY, RETURN),
            Pattern.CASE_INSENSITIVE);

    public static String extractRuntimeBytecode(String initBytecode) {
        // Check if the bytecode starts with "0x" and remove it if necessary
        if (initBytecode.startsWith(HEX_PREFIX)) {
            initBytecode = initBytecode.substring(2);
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
     * Checks if a given data string is likely init bytecode.
     *
     * @param data the data string to check.
     * @return true if it is init bytecode, false otherwise.
     */
    public static boolean isInitBytecode(final String data) {
        if (data == null || data.length() < MINIMUM_INIT_CODE_SIZE) {
            return false;
        }

        return INIT_BYTECODE_PATTERN.matcher(data).find();
    }

    public static boolean isValidInitBytecode(final String data) {
        return shouldSkipBytecodeCheck() || BytecodeUtils.isInitBytecode(data);
    }

    private static boolean shouldSkipBytecodeCheck() {
        return Boolean.parseBoolean(System.getenv(SKIP_INIT_CODE_CHECK));
    }
}
