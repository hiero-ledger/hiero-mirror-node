// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import lombok.experimental.UtilityClass;

/**
 * Parses untrusted protobuf with hardened limits so a maliciously-crafted payload that fits within the maximum request
 * size cannot exhaust the stack through deeply nested messages or over-allocate through too many/large repeated
 * elements. The maximum field size is bound to the length of the input itself, since no length-delimited field can
 * legitimately be larger than the message that contains it, so legitimate payloads are never rejected.
 */
@UtilityClass
public class ProtobufParser {

    /**
     * Maximum nesting depth of protobuf messages. Stricter than the pbj default of 128 to fail fast on recursive
     * payloads well before the JVM stack is exhausted.
     */
    public static final int MAX_DEPTH = 50;

    public static <T> T parse(final Codec<T> codec, final Bytes bytes) throws ParseException {
        // strictMode=true rejects unknown fields; parseUnknownFields=false so they are never buffered.
        return codec.parse(bytes.toReadableSequentialData(), true, false, MAX_DEPTH, Math.toIntExact(bytes.length()));
    }
}
