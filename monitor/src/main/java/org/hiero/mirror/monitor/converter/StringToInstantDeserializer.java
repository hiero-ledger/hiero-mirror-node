// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;

@CustomLog
public class StringToInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
        String text = jsonParser.getValueAsString();

        try {
            String[] parts = StringUtils.split(text, '.');
            if (parts != null && parts.length == 2) {
                return Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            }
        } catch (Exception e) {
            log.warn("Unable to parse timestamp: {}", text);
        }

        return null;
    }
}
