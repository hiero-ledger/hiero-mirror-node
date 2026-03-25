// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import static org.hiero.mirror.common.util.RuntimeHintsHelper.NONE;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerAnnotatedPackage;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerPackage;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerReflectionTypes;
import static org.hiero.mirror.common.util.RuntimeHintsHelper.registerResourcePatterns;

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleGroup;
import com.swirlds.config.api.ConfigData;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import lombok.CustomLog;
import org.hiero.mirror.rest.model.Error;
import org.hiero.mirror.restjava.config.RuntimeHintsConfiguration.CustomRuntimeHints;
import org.hiero.mirror.restjava.parameter.NumberRangeParameter;
import org.hiero.mirror.restjava.parameter.RequestParameter;
import org.jooq.Decfloat;
import org.jooq.Field;
import org.jooq.Geography;
import org.jooq.Geometry;
import org.jooq.JSON;
import org.jooq.JSONB;
import org.jooq.Result;
import org.jooq.RowId;
import org.jooq.XML;
import org.jooq.types.DayToSecond;
import org.jooq.types.UByte;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.jooq.types.YearToMonth;
import org.jooq.types.YearToSecond;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.type.filter.AssignableTypeFilter;

@Configuration(proxyBeanMethods = false)
@CustomLog
@ImportRuntimeHints(CustomRuntimeHints.class)
@NullMarked
final class RuntimeHintsConfiguration {

    static final class CustomRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
            final var loader = classLoader != null ? classLoader : getClass().getClassLoader();
            registerOpenApiModels(hints);
            registerRequestParameters(hints, loader);

            registerAnnotatedPackage(hints, loader, "com.hedera.node.config.data", ConfigData.class);
            registerPackage(hints, loader, ThrottleGroup.class.getPackageName());
            registerPackage(hints, loader, NumberRangeParameter.class.getPackageName());

            registerResourcePatterns(
                    hints,
                    "com/hedera/nativelib/hints/**",
                    "com/hedera/nativelib/wraps/**",
                    "semantic-version.properties",
                    "genesis/**");

            registerReflectionTypes(
                    hints,
                    NONE,
                    DayToSecond.class,
                    DayToSecond[].class,
                    UByte.class,
                    UByte[].class,
                    UInteger.class,
                    UInteger[].class,
                    ULong.class,
                    ULong[].class,
                    UShort.class,
                    UShort[].class,
                    YearToMonth.class,
                    YearToMonth[].class,
                    YearToSecond.class,
                    YearToSecond[].class,
                    LocalDate.class,
                    LocalTime.class,
                    LocalDateTime.class,
                    OffsetTime.class,
                    OffsetDateTime.class,
                    Instant.class,
                    Instant[].class,
                    Year.class,
                    LocalDate[].class,
                    LocalTime[].class,
                    LocalDateTime[].class,
                    OffsetTime[].class,
                    OffsetDateTime[].class,
                    Year[].class,
                    XML.class,
                    XML[].class,
                    Result.class,
                    Result[].class,
                    RowId.class,
                    RowId[].class,
                    Decfloat.class,
                    Decfloat[].class,
                    Field[].class,
                    Geography.class,
                    Geography[].class,
                    Geometry.class,
                    Geometry[].class,
                    JSON.class,
                    JSON[].class,
                    JSONB.class,
                    JSONB[].class,
                    org.jooq.Record.class,
                    org.jooq.Record[].class);
        }

        /**
         * Jackson uses reflection to instantiate the OpenAPI generated response models.
         *
         * @param hints The RuntimeHints to modify
         */
        private void registerOpenApiModels(RuntimeHints hints) {
            final var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
            scanner.findCandidateComponents(Error.class.getPackageName()).forEach(b -> hints.reflection()
                    .registerType(
                            TypeReference.of(b.getBeanClassName()),
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.ACCESS_DECLARED_FIELDS,
                            MemberCategory.INVOKE_PUBLIC_METHODS));
        }

        /**
         * RequestParameterArgumentResolver uses reflection to access fields and annotations on DTOs annotated with
         * {@link RequestParameter}.
         *
         * @param hints The RuntimeHints to modify
         */
        private void registerRequestParameters(RuntimeHints hints, ClassLoader loader) {
            registerAnnotatedPackage(
                    hints,
                    loader,
                    "org.hiero.mirror.restjava.dto",
                    RequestParameter.class,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }
}
