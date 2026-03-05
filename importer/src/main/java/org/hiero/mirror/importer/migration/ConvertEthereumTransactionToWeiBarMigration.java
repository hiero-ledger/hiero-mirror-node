// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.HashMap;
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
        var count = new AtomicLong(0);
        var stopwatch = Stopwatch.createStarted();
        final var jdbcOperations = jdbcOperationsProvider.getObject();
        final var parser = ethereumTransactionParserProvider.getObject();

        jdbcOperations.query(SELECT_TRANSACTIONS_SQL, rs -> {
            var consensusTimestamp = rs.getLong(1);
            var data = rs.getBytes(2);
            var parsed = parser.decode(data);

            // Use HashMap to allow null values (Map.of() doesn't allow nulls)
            var params = new HashMap<String, Object>();
            params.put("consensusTimestamp", consensusTimestamp);
            params.put("gasPrice", parsed.getGasPrice());
            params.put("maxFeePerGas", parsed.getMaxFeePerGas());
            params.put("maxPriorityFeePerGas", parsed.getMaxPriorityFeePerGas());
            params.put("value", parsed.getValue());

            jdbcOperations.update(UPDATE_SQL, params);
            count.incrementAndGet();
        });

        log.info(
                "Successfully converted gas and value fields from tinybar to weibar for {} ethereum transactions in {}",
                count.get(),
                stopwatch);
    }
}
