// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.rest.model.ServiceEndpoint;
import org.jspecify.annotations.Nullable;

public class StringToServiceEndpointConverter {

    public static final StringToServiceEndpointConverter INSTANCE = new StringToServiceEndpointConverter();

    public @Nullable ServiceEndpoint convert(String source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        try {
            return ObjectToStringSerializer.OBJECT_MAPPER.readValue(source, ServiceEndpoint.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse service endpoint", e);
        }
    }
}
