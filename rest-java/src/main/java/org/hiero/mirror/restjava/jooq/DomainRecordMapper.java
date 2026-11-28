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
import org.springframework.data.relational.core.mapping.Column;

class DomainRecordMapper<R extends Record, E> implements RecordMapper<R, E> {

    private static final Converter<String, String> COLUMN_CONVERTER =
            CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.LOWER_CAMEL);
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
            var column = member.getAnnotation(Column.class);
            var key = column != null ? column.value() : FORMAT_CONVERTER.convert(member.getName());
            if (!fieldNames.contains(key)) {
                continue;
            }

            // A field mapped to a differently named column (e.g. a JSONB holder) is exposed through the property
            // accessors named after the column, so resolve the setter and its type from the getter of that property
            var name = column != null ? COLUMN_CONVERTER.convert(key) : member.getName();
            var memberType = member.getType();
            var genericType = member.getGenericType();
            if (!name.equals(member.getName())) {
                var getter = type.getMethod(String.format("get%s", StringUtils.capitalize(name)));
                memberType = getter.getReturnType();
                genericType = getter.getGenericReturnType();
            }

            CollectionType collectionType = null;
            if (Collection.class.isAssignableFrom(memberType)) {
                var parameterizedType = (ParameterizedType) genericType;
                var typeFactory = OBJECT_MAPPER.getTypeFactory();
                var parameterType = typeFactory.constructType(parameterizedType.getActualTypeArguments()[0]);
                collectionType =
                        typeFactory.constructCollectionType((Class<? extends Collection<?>>) memberType, parameterType);
            }

            var methodName = String.format("set%s", StringUtils.capitalize(name));
            var methodHandle = LOOKUP.findVirtual(type, methodName, MethodType.methodType(void.class, memberType));

            setters.put(key, new Setter(collectionType, methodHandle));
        }

        return setters;
    }

    private record Setter(CollectionType collectionType, MethodHandle methodHandle) {}
}
