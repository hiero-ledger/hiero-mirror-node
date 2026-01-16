// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;

public class JsonbToListConverter implements GenericConverter {

    private static final ObjectMapper objectMapper = ObjectToStringSerializer.OBJECT_MAPPER;

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Collections.singleton(new ConvertiblePair(PGobject.class, List.class));
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        var elementTypeDescriptor = targetType.getElementTypeDescriptor();
        if (source instanceof PGobject pgo && elementTypeDescriptor != null) {
            var json = pgo.getValue();
            var type =
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementTypeDescriptor.getType());
            try {
                return objectMapper.readValue(json, type);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
