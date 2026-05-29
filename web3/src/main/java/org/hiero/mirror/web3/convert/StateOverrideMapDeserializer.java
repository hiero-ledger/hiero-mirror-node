// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.viewmodel.StateOverride;

/**
 * Deserializes {@code state_overrides} JSON array into a map keyed by EVM address.
 */
public class StateOverrideMapDeserializer extends JsonDeserializer<Map<String, StateOverride>> {

    private static final JavaType LIST_TYPE =
            TypeFactory.defaultInstance().constructCollectionType(List.class, StateOverride.class);

    @Override
    public Map<String, StateOverride> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }

        if (!parser.isExpectedStartArrayToken()) {
            throw context.wrongTokenException(parser, LIST_TYPE, JsonToken.START_ARRAY, "Expected array");
        }

        final var result = new LinkedHashMap<String, StateOverride>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            var override = context.readValue(parser, StateOverride.class);

            if (override.getState() != null
                    && !override.getState().isEmpty()
                    && override.getStateDiff() != null
                    && !override.getStateDiff().isEmpty()) {
                throw new InvalidParametersException("state and state_diff are mutually exclusive");
            }

            result.put(override.getAddress(), override);
        }

        return result.isEmpty() ? null : result;
    }
}
