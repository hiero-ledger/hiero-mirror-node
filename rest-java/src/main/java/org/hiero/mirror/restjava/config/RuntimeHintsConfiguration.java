// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import java.lang.reflect.Field;
import lombok.CustomLog;
import org.jooq.types.DayToSecond;
import org.jooq.types.UByte;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.jooq.types.YearToMonth;
import org.jooq.types.YearToSecond;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@CustomLog
@ImportRuntimeHints(RuntimeHintsConfiguration.CustomRuntimeHints.class)
class RuntimeHintsConfiguration {

    //    @PostConstruct
    //    void warmupJooq() {
    //        log.info("Warming up datatypes for jooq");
    //        var ignored1 = SQLDataType.VARCHAR;
    //        var ignored2 = SQLDataType.BIGINT;
    //        var ignored3 = SQLDataType.TIMESTAMP;
    //        var ignored4 = SQLDataType.UUID;
    //        var ignored5 = SQLDataType.JSONB;
    //        var ignored6 = SQLDataType.CLOB;
    //    }

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            try {
                Class<?> type = Class.forName(
                        "com.github.benmanes.caffeine.cache.SSSMSA",
                        false,
                        classLoader != null ? classLoader : getClass().getClassLoader());

                Field factory = type.getDeclaredField("FACTORY");
                hints.reflection().registerField(factory);

                hints.reflection()
                        .registerType(
                                TypeReference.of("org.hibernate.bytecode.internal.bytebuddy.BytecodeProviderImpl"),
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);

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

            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                // no-op;
            }
        }
    }
}
