// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.rest.model.ServiceEndpoint;

public class StringToServiceEndpointListConverter {

    public static final StringToServiceEndpointListConverter INSTANCE = new StringToServiceEndpointListConverter();
    private static final TypeReference<List<ServiceEndpoint>> TYPE_REF = new TypeReference<>() {};

    public List<ServiceEndpoint> convert(String source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return ObjectToStringSerializer.OBJECT_MAPPER.readValue(source, TYPE_REF);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse service endpoints", e);
        }
    }
}
