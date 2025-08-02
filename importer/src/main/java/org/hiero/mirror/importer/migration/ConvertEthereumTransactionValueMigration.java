// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.converter.WeiBarTinyBarConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
@RequiredArgsConstructor()
public class ConvertEthereumTransactionValueMigration extends AbstractJavaMigration {

    private static final String SELECT_NON_NULL_VALUE_SQL =
            "select consensus_timestamp, value " + "from ethereum_transaction "
                    + "where value is not null and length(value) > 0 "
                    + "order by consensus_timestamp";

    private static final String SET_TINYBAR_VALUE_SQL =
            "update ethereum_transaction " + "set value = :value " + "where consensus_timestamp = :consensusTimestamp";

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;

    @Override
    public String getDescription() {
        return "Convert ethereum transaction value from weibar to tinybar";
    }

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("1.60.0");
    }

    @Override
    protected void doMigrate() {
        var converter = WeiBarTinyBarConverter.INSTANCE;
        var count = new AtomicLong(0);
        var stopwatch = Stopwatch.createStarted();
        final var jdbcOperations = jdbcOperationsProvider.getObject();

        jdbcOperations.query(SELECT_NON_NULL_VALUE_SQL, rs -> {
            var consensusTimestamp = rs.getLong(1);
            var weibar = rs.getBytes(2);
            var tinybar = converter.convert(weibar, true);
            jdbcOperations.update(
                    SET_TINYBAR_VALUE_SQL, Map.of("consensusTimestamp", consensusTimestamp, "value", tinybar));
            count.incrementAndGet();
        });

        log.info(
                "Successfully converted value from weibar to tinybar for {} ethereum transactions in {}",
                count.get(),
                stopwatch);
    }
}
