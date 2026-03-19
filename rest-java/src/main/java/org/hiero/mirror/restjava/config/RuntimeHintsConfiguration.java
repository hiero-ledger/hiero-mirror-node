// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionField;

import lombok.CustomLog;
import org.jooq.types.DayToSecond;
import org.jooq.types.UByte;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.jooq.types.YearToMonth;
import org.jooq.types.YearToSecond;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@CustomLog
@ImportRuntimeHints(RuntimeHintsConfiguration.CustomRuntimeHints.class)
class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            final var loader = classLoader != null ? classLoader : getClass().getClassLoader();

            registerReflectionField(hints, loader, "com.github.benmanes.caffeine.cache.SSSMSA", "FACTORY");

            hints.reflection().registerType(TypeReference.of("org.jooq.impl.SQLDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.impl.SQLDataTypes$ClickHouseDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.impl.SQLDataTypes$DuckDBDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.impl.SQLDataTypes$TrinoDataType"));

            hints.reflection().registerType(DayToSecond[].class);
            hints.reflection().registerType(UByte[].class);
            hints.reflection().registerType(UInteger[].class);
            hints.reflection().registerType(ULong[].class);
            hints.reflection().registerType(UShort[].class);
            hints.reflection().registerType(YearToMonth[].class);
            hints.reflection().registerType(YearToSecond[].class);

            hints.reflection().registerType(TypeReference.of("org.jooq.util.cubrid.CUBRIDDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.derby.DerbyDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.firebird.FirebirdDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.h2.H2DataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.hsqldb.HSQLDBDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.ignite.IgniteDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.mariadb.MariaDBDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.mysql.MySQLDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.postgres.PostgresDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.sqlite.SQLiteDataType"));
            hints.reflection().registerType(TypeReference.of("org.jooq.util.yugabytedb.YugabyteDBDataType"));
            hints.reflection().registerType(org.jooq.Decfloat[].class);
        }
    }
}
