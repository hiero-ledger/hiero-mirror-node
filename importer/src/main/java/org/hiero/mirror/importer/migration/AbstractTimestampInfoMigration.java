// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.hiero.mirror.importer.repository.AccountBalanceFileRepository;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.support.TransactionTemplate;

abstract class AbstractTimestampInfoMigration extends TimeSensitiveBalanceMigration {

    private static final String GET_TIMESTAMP_INFO_SQL =
            """
            with last_record_file as (
              select consensus_end
              from record_file
              order by consensus_end desc
              limit 1
            )
            select
              consensus_timestamp as snapshot_timestamp,
              consensus_timestamp + time_offset as from_timestamp,
              consensus_end as to_timestamp
            from account_balance_file, last_record_file
            where synthetic is false and consensus_timestamp + time_offset <= consensus_end
            order by consensus_timestamp desc
            limit 1
            """;

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ObjectProvider<TransactionTemplate> transactionTemplateProvider;

    protected AbstractTimestampInfoMigration(
            ObjectProvider<AccountBalanceFileRepository> accountBalanceFileRepositoryProvider,
            Map<String, MigrationProperties> migrationPropertiesMap,
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            ObjectProvider<RecordFileRepository> recordFileRepositoryProvider,
            ObjectProvider<TransactionTemplate> transactionTemplateProvider) {
        super(migrationPropertiesMap, accountBalanceFileRepositoryProvider, recordFileRepositoryProvider);
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.transactionTemplateProvider = transactionTemplateProvider;
    }

    protected AtomicInteger doMigrate(String sql) {
        var count = new AtomicInteger();
        var jdbcOperations = jdbcOperationsProvider.getObject();
        transactionTemplateProvider.getObject().executeWithoutResult(s -> {
            try {
                var timestampInfo = jdbcOperations.queryForObject(
                        GET_TIMESTAMP_INFO_SQL, Collections.emptyMap(), new DataClassRowMapper<>(TimestampInfo.class));
                var params = new MapSqlParameterSource()
                        .addValue("snapshotTimestamp", timestampInfo.snapshotTimestamp())
                        .addValue("fromTimestamp", timestampInfo.fromTimestamp())
                        .addValue("toTimestamp", timestampInfo.toTimestamp());
                count.set(jdbcOperations.update(sql, params));
            } catch (EmptyResultDataAccessException e) {
                // GET_TIMESTAMP_INFO_SQL returns empty result
            }
        });

        return count;
    }

    private record TimestampInfo(long snapshotTimestamp, long fromTimestamp, long toTimestamp) {}
}
