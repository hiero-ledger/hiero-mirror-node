// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;

/*
 * Used by Hibernate to handle JSON/JSONB columns. We need this class since Hibernate doesn't provide a way to customize
 * its default ObjectMapper. Set via the `hibernate.type.json_format_mapper` property.
 */
@SuppressWarnings("unused")
public class CustomJsonFormatMapper implements FormatMapper {

    private final JacksonJsonFormatMapper delegate = new JacksonJsonFormatMapper(OBJECT_MAPPER);

    @Override
    public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
        return delegate.fromString(charSequence, javaType, wrapperOptions);
    }

    @Override
    public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
        return delegate.toString(value, javaType, wrapperOptions);
    }
}
