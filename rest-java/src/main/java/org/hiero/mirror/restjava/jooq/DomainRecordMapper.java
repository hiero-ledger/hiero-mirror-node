// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.jooq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.collect.Range;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.converter.LongRangeConverter;
import org.hiero.mirror.restjava.exception.RecordMappingException;
import org.jooq.EnumType;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.RecordType;
import org.jooq.postgres.extensions.types.LongRange;
import org.jspecify.annotations.Nullable;

class DomainRecordMapper<R extends Record, E> implements RecordMapper<R, E> {

    private static final Converter<String, String> FORMAT_CONVERTER =
            CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final ObjectMapper OBJECT_MAPPER = ObjectToStringSerializer.OBJECT_MAPPER;

    private final MethodHandle defaultConstructor;
    private final org.jooq.Field<?>[] recordFields;
    private final Map<String, Setter> setters;

    public DomainRecordMapper(RecordType<R> recordType, Class<? extends E> type) {
        try {
            defaultConstructor = LOOKUP.findConstructor(type, MethodType.methodType(void.class));
            recordFields = recordType.fields();
            var fieldNames =
                    Arrays.stream(recordFields).map(org.jooq.Field::getName).collect(Collectors.toSet());
            setters = getSetters(fieldNames, type);
        } catch (Exception e) {
            throw new RecordMappingException(
                    String.format("Failed to create record mapper for entity type %s", type.getName()), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable E map(R source) {
        try {
            var entity = (E) defaultConstructor.invoke();
            for (int i = 0; i < recordFields.length; i++) {
                var field = recordFields[i];
                var setter = setters.get(field.getName());
                var method = setter.methodHandle;
                var value = convert(setter, source.get(i));
                method.invoke(entity, value);
            }

            return entity;
        } catch (Throwable e) {
            throw new RecordMappingException("Failed to map record to entity", e);
        }
    }

    @SuppressWarnings({"java:S3776", "rawtypes", "unchecked"})
    private static Object convert(Setter setter, Object source) throws JsonProcessingException {
        if (source == null) {
            return null;
        }

        var collectionType = setter.collectionType;
        var sourceType = source.getClass();
        var targetType = setter.methodHandle.type().parameterType(1);

        if (targetType.isEnum()) {
            if (source instanceof EnumType enumValue) {
                return Enum.valueOf((Class<Enum>) targetType, enumValue.getLiteral());
            } else if (source instanceof Number number) {
                // TokenFreezeStatus and TokenKycStatus are stored as smallint instead of pg enum in the database
                return targetType.getEnumConstants()[number.shortValue()];
            }
        } else if (targetType == Range.class && source instanceof LongRange longRange) {
            return LongRangeConverter.INSTANCE.convert(longRange);
        } else if (targetType == EntityId.class && source instanceof Long id) {
            return EntityId.of(id);
        } else if (collectionType != null && source instanceof JSONB jsonb) {
            return OBJECT_MAPPER.readValue(jsonb.data(), collectionType);
        } else if (sourceType != targetType
                && source instanceof Number number
                && Number.class.isAssignableFrom(targetType)) {
            if (targetType == Integer.class) {
                return number.intValue();
            } else if (targetType == Long.class) {
                return number.longValue();
            }
        }

        return source;
    }

    private static List<Field> getInstanceMembers(Class<?> type) {
        var result = new ArrayList<Field>();

        do {
            for (var field : type.getDeclaredFields()) {
                if ((field.getModifiers() & Modifier.STATIC) == 0) {
                    result.add(field);
                }
            }

            type = type.getSuperclass();
        } while (type != null);

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Setter> getSetters(Set<String> fieldNames, Class<?> type)
            throws ReflectiveOperationException {
        var setters = new HashMap<String, Setter>();
        for (var member : getInstanceMembers(type)) {
            var name = member.getName();
            var key = FORMAT_CONVERTER.convert(name);
            if (!fieldNames.contains(key)) {
                continue;
            }

            CollectionType collectionType = null;
            var memberType = member.getType();
            if (Collection.class.isAssignableFrom(memberType)) {
                var parameterizedType = (ParameterizedType) member.getGenericType();
                var typeFactory = OBJECT_MAPPER.getTypeFactory();
                var parameterType = typeFactory.constructType(parameterizedType.getActualTypeArguments()[0]);
                collectionType =
                        typeFactory.constructCollectionType((Class<? extends Collection<?>>) memberType, parameterType);
            }

            var methodName = String.format("set%s", StringUtils.capitalize(name));
            var methodHandle =
                    LOOKUP.findVirtual(type, methodName, MethodType.methodType(void.class, member.getType()));

            setters.put(key, new Setter(collectionType, methodHandle));
        }

        return setters;
    }

    private record Setter(CollectionType collectionType, MethodHandle methodHandle) {}
}
