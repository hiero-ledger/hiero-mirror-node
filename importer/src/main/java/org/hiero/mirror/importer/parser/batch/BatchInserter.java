// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.CaseFormat;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.CustomLog;
import org.hiero.mirror.common.converter.EntityIdSerializer;
import org.hiero.mirror.common.converter.ListToStringSerializer;
import org.hiero.mirror.common.converter.RangeToStringSerializer;
import org.hiero.mirror.importer.converter.ByteArrayArrayToHexSerializer;
import org.hiero.mirror.importer.converter.ByteArrayToHexSerializer;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.CommonParserProperties;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.PGCopyOutputStream;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * Stateless writer to insert rows into PostgreSQL using COPY.
 */
@CustomLog
public class BatchInserter implements BatchPersister {

    protected final DataSource dataSource;
    protected final Timer latencyMetric;
    protected final MeterRegistry meterRegistry;
    protected final String tableName;

    private final Counter rowsMetric;
    private final String sql;
    private final ObjectWriter writer;
    private final CommonParserProperties properties;

    public BatchInserter(
            Class<?> entityClass,
            DataSource dataSource,
            MeterRegistry meterRegistry,
            CommonParserProperties properties) {
        this(entityClass, dataSource, meterRegistry, properties, entityClass.getSimpleName());
    }

    public BatchInserter(
            Class<?> entityClass,
            DataSource dataSource,
            MeterRegistry meterRegistry,
            CommonParserProperties properties,
            String tableName) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.tableName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, tableName);
        var mapper = new CsvMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(byte[][].class, ByteArrayArrayToHexSerializer.INSTANCE);
        module.addSerializer(byte[].class, ByteArrayToHexSerializer.INSTANCE);
        module.addSerializer(EntityIdSerializer.INSTANCE);
        module.addSerializer(ListToStringSerializer.INSTANCE);
        module.addSerializer(RangeToStringSerializer.INSTANCE);
        mapper.registerModule(module);
        mapper.configure(CsvGenerator.Feature.ALWAYS_QUOTE_EMPTY_STRINGS, true);
        var schema = mapper.schemaFor(entityClass);
        writer = mapper.writer(schema);
        String columnsCsv = Lists.newArrayList(schema.iterator()).stream()
                .map(CsvSchema.Column::getName)
                .distinct()
                .map(name -> CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name))
                .collect(Collectors.joining(", "));
        sql = String.format("COPY %s(%s) FROM STDIN WITH CSV", this.tableName, columnsCsv);
        var parentTableName = this.tableName.replaceAll("_\\d+$", ""); // Strip _01 shard suffix
        latencyMetric = Timer.builder(LATENCY_METRIC)
                .description("The time it took to batch insert rows")
                .tag("table", parentTableName)
                .tag("upsert", "false")
                .register(meterRegistry);
        rowsMetric = Counter.builder("hiero.mirror.importer.batch.rows")
                .description("The number of rows inserted into the table")
                .tag("table", parentTableName)
                .register(meterRegistry);
    }

    @Override
    public void persist(Collection<? extends Object> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);

        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            persistItems(items, connection);
            log.info("Copied {} rows to {} table in {}", items.size(), tableName, stopwatch);
        } catch (Exception e) {
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(), tableName), e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    protected void persistItems(Collection<?> items, Connection connection) throws SQLException, IOException {
        var stopwatch = Stopwatch.createStarted();
        PGConnection pgConnection = connection.unwrap(PGConnection.class);
        CopyIn copyIn = pgConnection.getCopyAPI().copyIn(sql);

        if (log.isTraceEnabled()) {
            String csv = writer.writeValueAsString(items);
            log.trace("Generated SQL: {}\n{}", sql, csv);
        }

        try (var pgCopyOutputStream = new PGCopyOutputStream(copyIn, properties.getBufferSize())) {
            writer.writeValue(pgCopyOutputStream, items);
            rowsMetric.increment(items.size());
            latencyMetric.record(stopwatch.elapsed());
        } finally {
            if (copyIn.isActive()) {
                copyIn.cancelCopy();
            }
        }
    }
}
