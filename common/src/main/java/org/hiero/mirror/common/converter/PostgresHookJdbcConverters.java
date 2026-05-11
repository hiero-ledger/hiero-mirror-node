// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.converter;

import java.sql.JDBCType;
import lombok.SneakyThrows;
import org.hiero.mirror.common.domain.hook.HookExtensionPoint;
import org.hiero.mirror.common.domain.hook.HookType;
import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.mapping.JdbcValue;

public final class PostgresHookJdbcConverters {

    private PostgresHookJdbcConverters() {}

    @WritingConverter
    public static final class HookExtensionPointToJdbcValue implements Converter<HookExtensionPoint, JdbcValue> {

        @Override
        @SneakyThrows
        public JdbcValue convert(HookExtensionPoint source) {
            if (source == null) {
                return JdbcValue.of(null, JDBCType.OTHER);
            }
            var pg = new PGobject();
            pg.setType("hook_extension_point");
            pg.setValue(source.name());
            return JdbcValue.of(pg, JDBCType.OTHER);
        }
    }

    @ReadingConverter
    public static final class PGobjectToHookExtensionPoint implements Converter<PGobject, HookExtensionPoint> {

        @Override
        public HookExtensionPoint convert(PGobject source) {
            if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
                return null;
            }
            return HookExtensionPoint.valueOf(source.getValue());
        }
    }

    @ReadingConverter
    public static final class StringToHookExtensionPoint implements Converter<String, HookExtensionPoint> {

        @Override
        public HookExtensionPoint convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return HookExtensionPoint.valueOf(source);
        }
    }

    @WritingConverter
    public static final class HookTypeToJdbcValue implements Converter<HookType, JdbcValue> {

        @Override
        @SneakyThrows
        public JdbcValue convert(HookType source) {
            if (source == null) {
                return JdbcValue.of(null, JDBCType.OTHER);
            }
            var pg = new PGobject();
            pg.setType("hook_type");
            pg.setValue(source.name());
            return JdbcValue.of(pg, JDBCType.OTHER);
        }
    }

    @ReadingConverter
    public static final class PGobjectToHookType implements Converter<PGobject, HookType> {

        @Override
        public HookType convert(PGobject source) {
            if (source == null || source.getValue() == null || source.getValue().isEmpty()) {
                return null;
            }
            return HookType.valueOf(source.getValue());
        }
    }

    @ReadingConverter
    public static final class StringToHookType implements Converter<String, HookType> {

        @Override
        public HookType convert(String source) {
            if (source == null || source.isEmpty()) {
                return null;
            }
            return HookType.valueOf(source);
        }
    }
}
