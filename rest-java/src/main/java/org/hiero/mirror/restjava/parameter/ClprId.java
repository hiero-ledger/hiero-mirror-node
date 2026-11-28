// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * A CLPR id, such as a channel id or a connector id, which is 32 bytes passed as 0x prefixed hex.
 */
public record ClprId(byte[] bytes) {

    private static final Pattern PATTERN = Pattern.compile("^0x[0-9a-f]{64}$", Pattern.CASE_INSENSITIVE);

    public static ClprId valueOf(String id) {
        if (!PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid CLPR id");
        }

        return new ClprId(HexFormat.of().parseHex(id, 2, id.length()));
    }
}
