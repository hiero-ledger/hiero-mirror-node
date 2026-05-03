// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.hiero.mirror.common.domain.hook.PostgresHookExtensionPoint;
import org.hiero.mirror.common.domain.hook.PostgresHookType;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

public final class PostgresHookJdbcConverters {

    private PostgresHookJdbcConverters() {}

    @WritingConverter
    public static final class PostgresHookExtensionPointToPGobject
            implements Converter<PostgresHookExtensionPoint, PGobject> {

        @Override
        @SneakyThrows
        public PGobject convert(PostgresHookExtensionPoint source) {
            if (source == null) {
                return null;
            }
            var pg = new PGobject();
            pg.setType("hook_extension_point");
            pg.setValue(source.getHookExtensionPoint().name());
            return pg;
        }
    }

    @ReadingConverter
    public static final class PGobjectToPostgresHookExtensionPoint
            implements Converter<PGobject, PostgresHookExtensionPoint> {

        @Override
        public PostgresHookExtensionPoint convert(PGobject source) {
            if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
                return null;
            }
            return PostgresHookExtensionPoint.of(HookExtensionPoint.valueOf(source.getValue()));
        }
    }

    @ReadingConverter
    public static final class StringToPostgresHookExtensionPoint
            implements Converter<String, PostgresHookExtensionPoint> {

        @Override
        public PostgresHookExtensionPoint convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return PostgresHookExtensionPoint.of(HookExtensionPoint.valueOf(source));
        }
    }

    @WritingConverter
    public static final class PostgresHookTypeToPGobject implements Converter<PostgresHookType, PGobject> {

        @Override
        @SneakyThrows
        public PGobject convert(PostgresHookType source) {
            if (source == null) {
                return null;
            }
            var pg = new PGobject();
            pg.setType("hook_type");
            pg.setValue(source.getHookType().name());
            return pg;
        }
    }

    @ReadingConverter
    public static final class PGobjectToPostgresHookType implements Converter<PGobject, PostgresHookType> {

        @Override
        public PostgresHookType convert(PGobject source) {
            if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
                return null;
            }
            return PostgresHookType.of(HookType.valueOf(source.getValue()));
        }
    }

    @ReadingConverter
    public static final class StringToPostgresHookType implements Converter<String, PostgresHookType> {

        @Override
        public PostgresHookType convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return PostgresHookType.of(HookType.valueOf(source));
        }
    }
}
