// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.parameter;

import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * A CLPR channel id query parameter. Channel ids are opaque 32-byte values, so only the {@code eq} operator is
 * supported.
 */
public final class ClprChannelIdParameter {

    private static final String ERROR = "Invalid channel.id parameter";
    private static final Pattern PATTERN =
            Pattern.compile("^((eq):)?(0x)?([0-9a-fA-F]{64})$", Pattern.CASE_INSENSITIVE);

    private final byte[] value;

    private ClprChannelIdParameter(byte[] value) {
        this.value = value;
    }

    public static ClprChannelIdParameter valueOf(String param) {
        final var matcher = PATTERN.matcher(param);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(ERROR);
        }

        return new ClprChannelIdParameter(HexFormat.of().parseHex(matcher.group(4)));
    }

    public byte[] value() {
        return value.clone();
    }
}
