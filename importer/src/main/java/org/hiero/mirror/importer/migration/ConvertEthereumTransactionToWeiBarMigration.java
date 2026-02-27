// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

@Named
public class ConvertEthereumTransactionToWeiBarMigration extends ConfigurableJavaMigration {

    private static final String SELECT_TRANSACTIONS_SQL =
            "select consensus_timestamp, data from ethereum_transaction order by consensus_timestamp";

    private static final String UPDATE_SQL = "update ethereum_transaction set gas_price = :gasPrice, "
            + "max_fee_per_gas = :maxFeePerGas, max_priority_fee_per_gas = :maxPriorityFeePerGas, "
            + "value = :value where consensus_timestamp = :consensusTimestamp";

    private final ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider;
    private final EthereumTransactionParser ethereumTransactionParser;
    private final boolean v2;

    ConvertEthereumTransactionToWeiBarMigration(
            Environment environment,
            ImporterProperties importerProperties,
            ObjectProvider<NamedParameterJdbcOperations> jdbcOperationsProvider,
            EthereumTransactionParser ethereumTransactionParser) {
        super(importerProperties.getMigration());
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
        this.jdbcOperationsProvider = jdbcOperationsProvider;
        this.ethereumTransactionParser = ethereumTransactionParser;
    }

    @Override
    public String getDescription() {
        return "Convert ethereum transaction gas and value fields from tinybar to weibar by re-parsing RLP data";
    }

    @Override
    public MigrationVersion getVersion() {
        return v2 ? MigrationVersion.fromVersion("2.23.0") : MigrationVersion.fromVersion("1.118.0");
    }

    @Override
    protected void doMigrate() {
        var count = new AtomicLong(0);
        var stopwatch = Stopwatch.createStarted();
        final var jdbcOperations = jdbcOperationsProvider.getObject();

        jdbcOperations.query(SELECT_TRANSACTIONS_SQL, rs -> {
            var consensusTimestamp = rs.getLong(1);
            var data = rs.getBytes(2);
            var parsed = ethereumTransactionParser.decode(data);
            jdbcOperations.update(
                    UPDATE_SQL,
                    Map.of(
                            "consensusTimestamp", consensusTimestamp,
                            "gasPrice", parsed.getGasPrice(),
                            "maxFeePerGas", parsed.getMaxFeePerGas(),
                            "maxPriorityFeePerGas", parsed.getMaxPriorityFeePerGas(),
                            "value", parsed.getValue()));
            count.incrementAndGet();
        });

        log.info(
                "Successfully converted gas and value fields from tinybar to weibar for {} ethereum transactions in {}",
                count.get(),
                stopwatch);
    }
}
