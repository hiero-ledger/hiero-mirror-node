// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;
import static org.hiero.mirror.importer.TestUtils.getResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.annotation.Resource;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.flywaydb.core.Flyway;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.config.CommonIntegrationTest;
import org.hiero.mirror.common.config.RedisTestConfiguration;
import org.hiero.mirror.common.converter.EntityIdConverter;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.node.ServiceEndpoint;
import org.hiero.mirror.common.tableusage.EndpointContext;
import org.hiero.mirror.importer.config.DateRangeCalculator;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.converter.JsonbToListConverter;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.jdbc.PgArray;
import org.postgresql.util.PGobject;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(SoftAssertionsExtension.class)
@Import(RedisTestConfiguration.class)
public abstract class ImporterIntegrationTest extends CommonIntegrationTest {

    protected static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    protected static final DomainBuilder DOMAIN_BUILDER = new DomainBuilder();

    private static final Map<Class<?>, String> DEFAULT_DOMAIN_CLASS_IDS = new ConcurrentHashMap<>();

    @Resource
    protected Flyway flyway;

    @Resource
    protected JdbcOperations jdbcOperations;

    @Owner
    @Resource
    protected JdbcTemplate ownerJdbcTemplate;

    @Resource
    protected ParserContext parserContext;

    @Resource
    protected RetryRecorder retryRecorder;

    @InjectSoftAssertions
    protected SoftAssertions softly;

    @Resource
    private DateRangeCalculator dateRangeCalculator;

    @Resource
    private ImporterProperties importerProperties;

    @Getter
    @Value("#{environment.matchesProfiles('!v2')}")
    private boolean v1;

    public static final <T> ObjectProvider<T> objectProvider(T t) {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() throws BeansException {
                return t;
            }
        };
    }

    protected static <T> RowMapper<T> rowMapper(Class<T> entityClass) {
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(
                PGobject.class, ServiceEndpoint.class, s -> convertJsonb(s, ServiceEndpoint.class));
        defaultConversionService.addConverter(
                PGobject.class, Range.class, source -> PostgreSQLGuavaRangeType.longRange(source.getValue()));
        defaultConversionService.addConverter(
                Long.class, EntityId.class, EntityIdConverter.INSTANCE::convertToEntityAttribute);
        defaultConversionService.addConverter(PgArray.class, List.class, array -> {
            try {
                return Arrays.asList((Object[]) array.getArray());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        defaultConversionService.addConverter(new JsonbToListConverter());

        DataClassRowMapper<T> dataClassRowMapper = new DataClassRowMapper<>(entityClass);
        dataClassRowMapper.setConversionService(defaultConversionService);
        return dataClassRowMapper;
    }

    private static <T> T convertJsonb(PGobject pgObject, Class<T> entityClass) {
        try {
            return OBJECT_MAPPER.readValue(pgObject.getValue(), entityClass);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void runRequiredRepeatableMigrations() {
        new RequiredRepeatableMigrationExecutor().run();
        EndpointContext.setCurrentEndpoint("/importer");
    }

    protected List<String> getRequiredRepeatableMigrations() {
        return Collections.emptyList();
    }

    protected <T> Collection<T> findEntity(Class<T> entityClass, String ids, String table) {
        String sql = String.format("select * from %s order by %s, timestamp_range asc", table, ids);
        return jdbcOperations.query(sql, rowMapper(entityClass));
    }

    protected <T> Collection<T> findHistory(Class<T> historyClass) {
        var ids = DEFAULT_DOMAIN_CLASS_IDS.computeIfAbsent(historyClass, this::getDefaultIdColumns);
        return findHistory(historyClass, ids);
    }

    protected <T> Collection<T> findHistory(Class<T> historyClass, String ids) {
        return findHistory(historyClass, ids, null);
    }

    protected <T> Collection<T> findHistory(String table, Class<T> historyClass) {
        var ids = DEFAULT_DOMAIN_CLASS_IDS.computeIfAbsent(historyClass, this::getDefaultIdColumns);
        return findHistory(historyClass, ids, table);
    }

    protected <T> Collection<T> findHistory(Class<T> historyClass, String ids, String table) {
        if (StringUtils.isEmpty(table)) {
            table = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, historyClass.getSimpleName());
        }

        if (!table.endsWith("_history")) {
            table += "_history";
        }

        return findEntity(historyClass, ids, table);
    }

    protected void reset() {
        super.reset();
        dateRangeCalculator.clear();
        importerProperties.setNetwork(ImporterProperties.HederaNetwork.TESTNET);
        importerProperties.setStartDate(Instant.EPOCH);
        parserContext.clear();
        retryRecorder.reset();
    }

    protected Boolean tableExists(String name) {
        return jdbcOperations.queryForObject(
                "select exists(select 1 from information_schema.tables where table_name = ?)", Boolean.class, name);
    }

    private String getDefaultIdColumns(Class<?> entityClass) {
        Stream<Field> idFields;
        var idClassAnnotation = AnnotationUtils.findAnnotation(entityClass, IdClass.class);
        if (idClassAnnotation != null) {
            var idClass = idClassAnnotation.value();
            idFields = Arrays.stream(idClass.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()));
        } else {
            idFields = Arrays.stream(FieldUtils.getAllFields(entityClass))
                    .filter(f -> AnnotationUtils.findAnnotation(f, Id.class) != null);
        }

        var idColumns = idFields.map(Field::getName)
                .map(name -> CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name))
                .collect(Collectors.joining(","));

        return !idColumns.isEmpty() ? idColumns : "id";
    }

    private class RequiredRepeatableMigrationExecutor {

        private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(.*)}");

        @SneakyThrows
        void run() {
            for (var migration : getRequiredRepeatableMigrations()) {
                String sql = render(FileUtils.readFileToString(getResource(migration), StandardCharsets.UTF_8));
                ownerJdbcTemplate.execute(sql);
            }
        }

        private String render(String sql) {
            var matcher = PLACEHOLDER_PATTERN.matcher(sql);
            var placeHolderKeys = new HashSet<String>();
            while (matcher.find()) {
                placeHolderKeys.add(matcher.group(1));
            }

            if (placeHolderKeys.isEmpty()) {
                return sql;
            }

            var placeHolders = flyway.getConfiguration().getPlaceholders();
            for (String key : placeHolderKeys) {
                sql = sql.replaceAll("\\$\\{" + key + "}", placeHolders.get(key));
            }

            return sql;
        }
    }
}
