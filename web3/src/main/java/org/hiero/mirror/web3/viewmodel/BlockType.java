// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * BlockType represents a way to identify a specific block in the chain. Can be one of:
 *  - block tag ("earliest","latest", "safe", "pending", "finalized")
 *  - block number (decimal or hex string)
 *  - block hash (hex string of length 64 or 96, with or without 0x prefix)
 */
public record BlockType(String name, long number) {

    private static final Pattern BLOCK_PATTERN = Pattern.compile(
            "^(?:" + "(?<tag>earliest|finalized|latest|pending|safe)"
                    + "|(?<decimal>\\d{1,20})"
                    + "|(?:0x)?(?<hash>[0-9a-fA-F]{64}|[0-9a-fA-F]{96})"
                    + "|(?:0x)?(?<hexNum>[0-9a-fA-F]{1,63}|[0-9a-fA-F]{65,95})"
                    + ")$",
            Pattern.CASE_INSENSITIVE);

    public static final BlockType EARLIEST = new BlockType("earliest", 0L);
    public static final BlockType LATEST = new BlockType("latest", Long.MAX_VALUE);

    /**
     * Value for number when blockType represents a block hash,
     * name holds the normalized hex string (0x prefix + lowercase hex digits).
     */
    public static final long BLOCK_HASH_SENTINEL = -1L;

    public boolean isHash() {
        return number == BLOCK_HASH_SENTINEL;
    }

    @JsonCreator
    public static BlockType of(final String value) {
        if (StringUtils.isEmpty(value)) {
            return LATEST;
        }

        final var m = BLOCK_PATTERN.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid block value: " + value);
        }

        final var tag = m.group("tag");
        if (tag != null) {
            return blockTypeForTag(tag.toLowerCase());
        }

        final var decimal = m.group("decimal");
        if (decimal != null) {
            return new BlockType(value, Long.parseLong(decimal, 10));
        }

        final var hash = m.group("hash");
        if (hash != null) {
            return new BlockType(hash.toLowerCase(), BLOCK_HASH_SENTINEL);
        }

        final var hexNum = m.group("hexNum");
        if (hexNum != null) {
            return new BlockType(hexNum.toLowerCase(), Long.parseLong(hexNum, 16));
        }
        throw new IllegalArgumentException("Invalid block value: " + value);
    }

    private static BlockType blockTypeForTag(String tag) {
        return switch (tag) {
            case "earliest" -> EARLIEST;
            case "finalized", "latest", "pending", "safe" -> LATEST;
            default -> throw new IllegalStateException("Unexpected block tag: " + tag);
        };
    }

    public String toString() {
        if (this == EARLIEST || this == LATEST || isHash()) {
            return name;
        }

        return String.valueOf(number);
    }
}
