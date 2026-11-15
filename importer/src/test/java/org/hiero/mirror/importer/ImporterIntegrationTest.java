// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import static org.hiero.mirror.common.converter.ObjectToStringSerializer.OBJECT_MAPPER;
import static org.hiero.mirror.importer.TestUtils.getResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.annotation.Resource;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.hiero.mirror.importer.config.ActuatorHttpServer;
import org.hiero.mirror.importer.config.DateRangeCalculator;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.converter.JsonbToListConverter;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.entity.ParserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.jdbc.PgArray;
import org.postgresql.util.PGobject;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ExtendWith(SoftAssertionsExtension.class)
@Import(RedisTestConfiguration.class)
public abstract class ImporterIntegrationTest extends CommonIntegrationTest {

    protected static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    protected static final DomainBuilder DOMAIN_BUILDER = new DomainBuilder();

    private static final Map<Class<?>, String> DEFAULT_DOMAIN_CLASS_IDS = new ConcurrentHashMap<>();

    // Disable HTTP server during tests
    @MockitoBean
    private ActuatorHttpServer actuatorHttpServer;

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
    private DBProperties dbProperties;

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
        final var conversionService = new DefaultConversionService();
        conversionService.addConverter(
                PGobject.class, ServiceEndpoint.class, s -> convertJsonb(s, ServiceEndpoint.class));
        conversionService.addConverter(
                PGobject.class, Range.class, source -> PostgreSQLGuavaRangeType.longRange(source.getValue()));
        conversionService.addConverter(
                Long.class, EntityId.class, EntityIdConverter.INSTANCE::convertToEntityAttribute);
        conversionService.addConverter(PgArray.class, List.class, array -> {
            try {
                return Arrays.asList((Object[]) array.getArray());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        conversionService.addConverter(new JsonbToListConverter());

        final var dataClassRowMapper = new DataClassRowMapper<>(entityClass);
        dataClassRowMapper.setConversionService(conversionService);

        // DataClassRowMapper can't populate @Embedded fields (e.g. ClprChannel.peerThrottle), since their columns
        // don't match any constructor parameter by name; map those separately onto the row it already built.
        final var embeddedFields = Arrays.stream(FieldUtils.getAllFields(entityClass))
                .filter(field -> field.isAnnotationPresent(Embedded.class))
                .toList();
        if (embeddedFields.isEmpty()) {
            return dataClassRowMapper;
        }

        return (rs, rowNum) -> {
            final var entity = dataClassRowMapper.mapRow(rs, rowNum);
            final var entityWrapper = new BeanWrapperImpl(entity);
            final var columns = resultSetColumns(rs.getMetaData());
            for (final var embeddedField : embeddedFields) {
                final var embedded = mapEmbedded(embeddedField, rs, columns, conversionService);
                if (embedded != null) {
                    entityWrapper.setPropertyValue(embeddedField.getName(), embedded);
                }
            }
            return entity;
        };
    }

    private static String columnName(Field field) {
        final var columnAnnotation = field.getAnnotation(Column.class);
        return columnAnnotation != null && !columnAnnotation.name().isEmpty()
                ? columnAnnotation.name()
                : CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getName());
    }

    private static <T> T convertJsonb(PGobject pgObject, Class<T> entityClass) {
        try {
            return OBJECT_MAPPER.readValue(pgObject.getValue(), entityClass);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // Returns null, matching Hibernate's own behavior of leaving an @Embedded field null when every one of its
    // columns is null, rather than setting it to an all-null instance.
    private static Object mapEmbedded(
            Field embeddedField, ResultSet rs, Set<String> columns, ConversionService conversionService)
            throws SQLException {
        final var overrides = Arrays.stream(embeddedField.getAnnotationsByType(AttributeOverride.class))
                .collect(Collectors.toMap(
                        AttributeOverride::name, override -> override.column().name()));

        final var embedded = BeanUtils.instantiateClass(embeddedField.getType());
        final var embeddedWrapper = new BeanWrapperImpl(embedded);
        embeddedWrapper.setConversionService(conversionService);

        boolean hasValues = false;
        for (final var field : FieldUtils.getAllFields(embeddedField.getType())) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            final var column = overrides.getOrDefault(field.getName(), columnName(field));
            if (!columns.contains(column.toLowerCase(Locale.ROOT))) {
                continue;
            }

            final var value = rs.getObject(column);
            if (value != null) {
                embeddedWrapper.setPropertyValue(field.getName(), value);
                hasValues = true;
            }
        }

        return hasValues ? embedded : null;
    }

    private static Set<String> resultSetColumns(ResultSetMetaData metadata) throws SQLException {
        final var columns = new HashSet<String>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            columns.add(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT));
        }
        return columns;
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
                "select exists(select 1 from information_schema.tables where table_schema = ? and table_name = ?)",
                Boolean.class,
                dbProperties.getSchema(),
                name);
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
