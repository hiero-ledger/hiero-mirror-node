// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * BlockType represents a way to identify a specific block in the chain. Can be one of:
 *  - block tag ("earliest","latest", "safe", "pending", "finalized")
 *  - block number (decimal or hex string)
 *  - block hash (hex string of length 96, with or without 0x prefix)
 */
public record BlockType(String name, long number) {

    public static final BlockType EARLIEST = new BlockType("earliest", 0L);
    public static final BlockType LATEST = new BlockType("latest", Long.MAX_VALUE);

    /**
     * Value for number when blockType represents a block hash,
     * name holds the normalized hex string (0x prefix + lowercase hex digits).
     */
    public static final long BLOCK_HASH_SENTINEL = -1L;

    public static final int RECORD_FILE_HASH_HEX_LENGTH = 96;

    private static final String HEX_PREFIX = "0x";
    private static final String NEGATIVE_NUMBER_PREFIX = "-";

    public boolean isHash() {
        return number == BLOCK_HASH_SENTINEL;
    }

    @JsonCreator
    public static BlockType of(final String value) {
        if (StringUtils.isEmpty(value)) {
            return LATEST;
        }
        final String blockTypeName = value.toLowerCase();

        return switch (blockTypeName) {
            case "earliest" -> EARLIEST;
            case "latest", "safe", "pending", "finalized" -> LATEST;
            default -> parseNonNamedBlock(value);
        };
    }

    private static boolean isHex(String hex) {
        for (int i = 0; i < hex.length(); i++) {
            if (!isValidHexChar(hex.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidHexChar(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    private static BlockType parseBlockHash(String value) {
        final var hex = Strings.CS.removeStart(value.toLowerCase(), HEX_PREFIX);
        if (hex.length() != RECORD_FILE_HASH_HEX_LENGTH || !isHex(hex)) {
            return null;
        }
        return new BlockType(HEX_PREFIX + hex, BLOCK_HASH_SENTINEL);
    }

    private static BlockType parseNumericBlock(String value) {
        final boolean isHex = value.startsWith(HEX_PREFIX);

        final String noPrefixValue = isHex ? value.substring(HEX_PREFIX.length()) : value;

        if (noPrefixValue.startsWith(NEGATIVE_NUMBER_PREFIX)) {
            throw new IllegalArgumentException("Invalid block value: " + value);
        }

        final int radix = isHex ? 16 : 10;
        try {
            final long blockNumber = Long.parseLong(noPrefixValue, radix);
            return new BlockType(value, blockNumber);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid block value: " + value, e);
        }
    }

    private static BlockType parseNonNamedBlock(String value) {
        final var blockHash = parseBlockHash(value);
        if (blockHash != null) {
            return blockHash;
        }
        return parseNumericBlock(value);
    }

    public String toString() {
        if (this == EARLIEST || this == LATEST || isHash()) {
            return name;
        }

        return String.valueOf(number);
    }
}
