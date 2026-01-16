// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

public record BlockType(String name, long number) {

    public static final BlockType EARLIEST = new BlockType("earliest", 0L);
    public static final BlockType LATEST = new BlockType("latest", Long.MAX_VALUE);

    private static final String HEX_PREFIX = "0x";
    private static final String NEGATIVE_NUMBER_PREFIX = "-";

    public static BlockType of(final String value) {
        if (StringUtils.isEmpty(value)) {
            return LATEST;
        }

        final String blockTypeName = value.toLowerCase();
        switch (blockTypeName) {
            case "earliest" -> {
                return EARLIEST;
            }
            case "latest", "safe", "pending", "finalized" -> {
                return LATEST;
            }
            default -> {
                return extractNumericBlock(value);
            }
        }
    }

    private static BlockType extractNumericBlock(String value) {
        int radix = 10;
        var cleanedValue = value;

        if (value.startsWith(HEX_PREFIX)) {
            radix = 16;
            cleanedValue = Strings.CS.removeStart(value, HEX_PREFIX);
        }

        if (cleanedValue.contains(NEGATIVE_NUMBER_PREFIX)) {
            throw new IllegalArgumentException("Invalid block value: " + value);
        }

        try {
            long blockNumber = Long.parseLong(cleanedValue, radix);
            return new BlockType(value, blockNumber);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid block value: " + value, e);
        }
    }

    public String toString() {
        if (this == EARLIEST || this == LATEST) {
            return name;
        }

        return String.valueOf(number);
    }
}
