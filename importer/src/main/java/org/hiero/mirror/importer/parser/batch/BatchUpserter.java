// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.batch;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lombok.CustomLog;
import org.hiero.mirror.importer.exception.ParserException;
import org.hiero.mirror.importer.parser.CommonParserProperties;
import org.hiero.mirror.importer.repository.upsert.UpsertQueryGenerator;
import org.springframework.util.CollectionUtils;

/**
 * Stateless writer to upsert rows into PostgreSQL using COPY into a temp table then insert and update into final table
 */
@CustomLog
public class BatchUpserter extends BatchInserter {

    private final String finalTableName;
    private final String tempTableCleanupSql;
    private final String upsertSql;
    private final Timer upsertMetric;

    public BatchUpserter(
            Class<?> entityClass,
            DataSource dataSource,
            MeterRegistry meterRegistry,
            CommonParserProperties properties,
            UpsertQueryGenerator upsertQueryGenerator) {
        super(entityClass, dataSource, meterRegistry, properties, upsertQueryGenerator.getTemporaryTableName());
        tempTableCleanupSql = String.format("truncate table %s restart identity cascade", tableName);
        finalTableName = upsertQueryGenerator.getFinalTableName();
        upsertSql = upsertQueryGenerator.getUpsertQuery();
        log.trace("Table: {}, Entity: {}, upsertSql:\n{}", finalTableName, entityClass, upsertSql);
        upsertMetric = Timer.builder(LATENCY_METRIC)
                .description("The time it took to batch insert rows")
                .tag("table", finalTableName)
                .tag("upsert", "true")
                .register(meterRegistry);
    }

    @Override
    protected void persistItems(Collection<?> items, Connection connection) {
        if (CollectionUtils.isEmpty(items)) {
            return;
        }

        try {
            // create temp table to copy into
            cleanupTempTable(connection);

            // copy items to temp table
            super.persistItems(items, connection);

            // Upsert items from the temporary table to the final table
            upsert(connection);
        } catch (Exception e) {
            throw new ParserException(
                    String.format("Error copying %d items to table %s", items.size(), finalTableName), e);
        }
    }

    private void cleanupTempTable(Connection connection) throws SQLException {
        try (var preparedStatement = connection.prepareStatement(tempTableCleanupSql)) {
            preparedStatement.execute();
        }

        log.trace("Cleaned temp table {}", tableName);
    }

    private void upsert(Connection connection) throws SQLException {
        var startTime = System.nanoTime();

        try (PreparedStatement preparedStatement = connection.prepareStatement(upsertSql)) {
            preparedStatement.execute();
            log.debug("Upserted data from table {} to table {}", tableName, finalTableName);
        } finally {
            upsertMetric.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }
}
