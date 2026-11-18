// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import org.jspecify.annotations.NullMarked;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Accepts only a JSON integer literal for a {@link Long} field. Jackson's default coercion silently accepts strings
 * ({@code "100"}), floats ({@code 1.5} truncated to {@code 1}), and exponents ({@code 1e2}); this rejects them. JSON
 * {@code null} never reaches this deserializer and still maps to {@code null}.
 */
@NullMarked
public final class StrictLongDeserializer extends StdDeserializer<Long> {

    public StrictLongDeserializer() {
        super(Long.class);
    }

    @Override
    public Long deserialize(final JsonParser parser, final DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return (Long) context.handleUnexpectedToken(Long.class, parser);
        }
        return parser.getLongValue();
    }
}
