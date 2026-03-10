// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
final class ConvertEthereumTransactionToWeiBarMigration extends ConfigurableJavaMigration {

    private static final int BATCH_SIZE = 1000;
    private static final String SELECT_TRANSACTIONS_SQL =
            "select consensus_timestamp, data from ethereum_transaction order by consensus_timestamp";

    private static final String UPDATE_SQL = "update ethereum_transaction set gas_price = ?, "
            + "max_fee_per_gas = ?, max_priority_fee_per_gas = ?, "
            + "value = ? where consensus_timestamp = ?";

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final ObjectProvider<EthereumTransactionParser> ethereumTransactionParserProvider;
    private final boolean v2;

    ConvertEthereumTransactionToWeiBarMigration(
            Environment environment,
            ImporterProperties importerProperties,
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            ObjectProvider<EthereumTransactionParser> ethereumTransactionParserProvider) {
        super(importerProperties.getMigration());
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.ethereumTransactionParserProvider = ethereumTransactionParserProvider;
    }

    @Override
    public String getDescription() {
        return "Convert ethereum transaction gas and value fields from tinybar to weibar by re-parsing RLP data";
    }

    @Override
    public MigrationVersion getVersion() {
        return v2 ? MigrationVersion.fromVersion("2.24.0") : MigrationVersion.fromVersion("1.119.0");
    }

    @Override
    protected void doMigrate() {
        var stopwatch = Stopwatch.createStarted();
        final var jdbcOperations = jdbcOperationsProvider.getObject();
        final var parser = ethereumTransactionParserProvider.getObject();

        // Track total count and failed count across batches
        var totalCount = new AtomicLong(0);
        var failedCount = new AtomicLong(0);

        // Process transactions in batches to avoid memory issues
        var updates = new ArrayList<TransactionUpdate>(BATCH_SIZE);
        jdbcOperations.query(SELECT_TRANSACTIONS_SQL, rs -> {
            var consensusTimestamp = rs.getLong(1);
            var data = rs.getBytes(2);

            try {
                var parsed = parser.decode(data);

                updates.add(new TransactionUpdate(
                        consensusTimestamp,
                        parsed.getGasPrice(),
                        parsed.getMaxFeePerGas(),
                        parsed.getMaxPriorityFeePerGas(),
                        parsed.getValue()));

                // Flush batch when it reaches BATCH_SIZE
                if (updates.size() >= BATCH_SIZE) {
                    totalCount.addAndGet(batchUpdate(jdbcOperations, updates));
                    updates.clear();
                }
            } catch (Exception e) {
                failedCount.incrementAndGet();
                log.warn(
                        "Failed to decode ethereum transaction at consensus timestamp {}: {}",
                        consensusTimestamp,
                        e.getMessage());
            }
        });

        // Flush any remaining updates
        if (!updates.isEmpty()) {
            totalCount.addAndGet(batchUpdate(jdbcOperations, updates));
        }

        log.info(
                "Successfully converted gas and value fields from tinybar to weibar for {} ethereum transactions in {}",
                totalCount.get(),
                stopwatch);
        if (failedCount.get() > 0) {
            log.warn("Failed to decode {} ethereum transactions due to invalid RLP data", failedCount.get());
        }
    }

    private int batchUpdate(NamedParameterJdbcOperations namedJdbcOperations, List<TransactionUpdate> updates) {
        // Get underlying JdbcOperations for more efficient batch update
        var jdbcOperations = namedJdbcOperations.getJdbcOperations();

        jdbcOperations.batchUpdate(UPDATE_SQL, updates, updates.size(), (ps, update) -> {
            ps.setBytes(1, update.gasPrice);
            ps.setBytes(2, update.maxFeePerGas);
            ps.setBytes(3, update.maxPriorityFeePerGas);
            ps.setBytes(4, update.value);
            ps.setLong(5, update.consensusTimestamp);
        });

        log.debug("Batch updated {} ethereum transactions", updates.size());
        return updates.size();
    }

    private record TransactionUpdate(
            long consensusTimestamp, byte[] gasPrice, byte[] maxFeePerGas, byte[] maxPriorityFeePerGas, byte[] value) {}
}
