// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

public final class PostgresEntityTypeJdbcConverters {

    private PostgresEntityTypeJdbcConverters() {}

    @WritingConverter
    public static final class EntityTypeToJdbcValue implements Converter<EntityType, JdbcValue> {

        @Override
        @SneakyThrows
        public JdbcValue convert(EntityType source) {
            if (source == null) {
                return null;
            }
            var pg = new PGobject();
            pg.setType("entity_type");
            pg.setValue(source.name());
            return JdbcValue.of(pg, JDBCType.OTHER);
        }
    }

    @ReadingConverter
    public static final class PGobjectToEntityType implements Converter<PGobject, EntityType> {

        @Override
        public EntityType convert(PGobject source) {
            if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
                return null;
            }
            return EntityType.valueOf(source.getValue());
        }
    }

    @ReadingConverter
    public static final class StringToEntityType implements Converter<String, EntityType> {

        @Override
        public EntityType convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return EntityType.valueOf(source);
        }
    }
}
