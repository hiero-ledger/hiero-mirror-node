// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.spec.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class JsonAsStringDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        return jp.readValueAsTree().toString();
    }
}
