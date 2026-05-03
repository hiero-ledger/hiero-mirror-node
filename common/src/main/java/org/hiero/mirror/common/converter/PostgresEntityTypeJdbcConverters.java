// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.entity.PostgresEntityType;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

public final class PostgresEntityTypeJdbcConverters {

    private PostgresEntityTypeJdbcConverters() {}

    @WritingConverter
    public static final class PostgresEntityTypeToPGobject implements Converter<PostgresEntityType, PGobject> {

        @Override
        @SneakyThrows
        public PGobject convert(PostgresEntityType source) {
            if (source == null) {
                return null;
            }
            var pg = new PGobject();
            pg.setType("entity_type");
            pg.setValue(source.getEntityType().name());
            return pg;
        }
    }

    @ReadingConverter
    public static final class PGobjectToPostgresEntityType implements Converter<PGobject, PostgresEntityType> {

        @Override
        public PostgresEntityType convert(PGobject source) {
            if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
                return null;
            }
            return PostgresEntityType.of(EntityType.valueOf(source.getValue()));
        }
    }

    /**
     * Some JDBC stacks return Postgres {@code entity_type} enum columns as plain strings instead of {@link PGobject}.
     */
    @ReadingConverter
    public static final class StringToPostgresEntityType implements Converter<String, PostgresEntityType> {

        @Override
        public PostgresEntityType convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return PostgresEntityType.of(EntityType.valueOf(source));
        }
    }
}
