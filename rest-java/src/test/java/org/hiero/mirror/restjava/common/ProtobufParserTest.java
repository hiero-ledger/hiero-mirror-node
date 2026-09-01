// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.Key;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;

final class ProtobufParserTest {

    // The parse starts the depth budget at MAX_DEPTH and the generated codec decrements it for every nested message,
    // throwing once it goes negative (so MAX_DEPTH + 1 nested messages are parsed). A zero-length innermost message is
    // resolved to its default without being recursed into, so it costs no depth; with this alternating Key/KeyList
    // chain that makes MAX_DEPTH + 2 messages the deepest payload the parser accepts, and one more is rejected.
    private static final int MAX_ACCEPTED_MESSAGES = ProtobufParser.MAX_DEPTH + 2;

    @Test
    void parsesAtMaxDepth() throws ParseException {
        final var key = ProtobufParser.parse(Key.PROTOBUF, nestedKey(MAX_ACCEPTED_MESSAGES));

        assertThat(key).isNotNull();
    }

    @Test
    void rejectsBeyondMaxDepth() {
        // One message deeper than the limit is rejected instead of recursing until the stack overflows.
        assertThatThrownBy(() -> ProtobufParser.parse(Key.PROTOBUF, nestedKey(MAX_ACCEPTED_MESSAGES + 1)))
                .isInstanceOf(ParseException.class);
    }

    /**
     * Builds the serialized body of a {@link Key} nested {@code messageCount} levels deep, alternating
     * {@code Key.keyList} (field 6) and {@code KeyList.keys} (field 1) so every level is exactly one message, i.e. one
     * decrement of the parser's depth budget. The innermost message is empty.
     */
    private static Bytes nestedKey(final int messageCount) {
        var content = new byte[0];
        for (int message = messageCount - 1; message >= 1; message--) {
            if (content.length >= 0x80) {
                throw new IllegalStateException("Nested length no longer fits in a single-byte varint");
            }
            // Odd messages are Key (wrapping field keyList=6), even messages are KeyList (wrapping field keys=1),
            // both with wire type 2 (length-delimited): tag = (fieldNumber << 3) | 2.
            final int tag = message % 2 == 1 ? 0x32 : 0x0A;
            final var wrapped = new byte[content.length + 2];
            wrapped[0] = (byte) tag;
            wrapped[1] = (byte) content.length;
            System.arraycopy(content, 0, wrapped, 2, content.length);
            content = wrapped;
        }
        return Bytes.wrap(content);
    }
}
