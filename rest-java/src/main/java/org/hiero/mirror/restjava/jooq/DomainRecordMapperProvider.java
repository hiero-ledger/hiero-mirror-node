// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.jooq;

import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.RecordMapperProvider;
import org.jooq.RecordType;
import org.jooq.impl.DefaultRecordMapper;

@Named
public class DomainRecordMapperProvider implements RecordMapperProvider {

    private static final String PACKAGE_PREFIX = "org.hiero.mirror";

    private final Map<MapperKey, RecordMapper<?, ?>> mappers = new ConcurrentHashMap<>();

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public <R extends Record, E> RecordMapper<R, E> provide(RecordType<R> recordType, Class<? extends E> type) {
        var key = new MapperKey(recordType, type);
        return (RecordMapper<R, E>) mappers.computeIfAbsent(key, k -> {
            if (type.getName().startsWith(PACKAGE_PREFIX)) {
                return new DomainRecordMapper<>(recordType, type);
            }

            return new DefaultRecordMapper<>(recordType, type);
        });
    }

    private record MapperKey(RecordType<?> recordType, Class<?> type) {}
}
