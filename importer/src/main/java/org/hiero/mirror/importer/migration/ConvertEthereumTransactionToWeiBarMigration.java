// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
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

    private static final String UPDATE_SQL = "update ethereum_transaction set gas_price = :gasPrice, "
            + "max_fee_per_gas = :maxFeePerGas, max_priority_fee_per_gas = :maxPriorityFeePerGas, "
            + "value = :value where consensus_timestamp = :consensusTimestamp";

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

        // Track total count across batches
        var totalCountWrapper = new int[] {0};

        // Process transactions in batches to avoid memory issues
        var updates = new ArrayList<TransactionUpdate>(BATCH_SIZE);
        jdbcOperations.query(SELECT_TRANSACTIONS_SQL, rs -> {
            var consensusTimestamp = rs.getLong(1);
            var data = rs.getBytes(2);
            var parsed = parser.decode(data);

            updates.add(new TransactionUpdate(
                    consensusTimestamp,
                    parsed.getGasPrice(),
                    parsed.getMaxFeePerGas(),
                    parsed.getMaxPriorityFeePerGas(),
                    parsed.getValue()));

            // Flush batch when it reaches BATCH_SIZE
            if (updates.size() >= BATCH_SIZE) {
                totalCountWrapper[0] += batchUpdate(jdbcOperations, updates);
                updates.clear();
            }
        });

        // Flush any remaining updates
        if (!updates.isEmpty()) {
            totalCountWrapper[0] += batchUpdate(jdbcOperations, updates);
        }

        log.info(
                "Successfully converted gas and value fields from tinybar to weibar for {} ethereum transactions in {}",
                totalCountWrapper[0],
                stopwatch);
    }

    private int batchUpdate(NamedParameterJdbcOperations jdbcOperations, List<TransactionUpdate> updates) {
        var batchParams = updates.stream()
                .map(update -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("consensusTimestamp", update.consensusTimestamp);
                    params.put("gasPrice", update.gasPrice);
                    params.put("maxFeePerGas", update.maxFeePerGas);
                    params.put("maxPriorityFeePerGas", update.maxPriorityFeePerGas);
                    params.put("value", update.value);
                    return params;
                })
                .toArray(Map[]::new);

        jdbcOperations.batchUpdate(UPDATE_SQL, batchParams);
        log.debug("Batch updated {} ethereum transactions", updates.size());
        return updates.size();
    }

    @Data
    private static class TransactionUpdate {
        private final long consensusTimestamp;
        private final byte[] gasPrice;
        private final byte[] maxFeePerGas;
        private final byte[] maxPriorityFeePerGas;
        private final byte[] value;
    }
}
