// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionParser;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionOperations;

@Named
final class ConvertEthereumTransactionToWeiBarMigration extends AsyncJavaMigration<Long> {

    private static final String SELECT_TRANSACTIONS_SQL = """
            select consensus_timestamp, data, payer_account_id
            from ethereum_transaction
            where consensus_timestamp < :consensusTimestamp
            order by consensus_timestamp desc
            limit 5000
            """;

    private static final String UPDATE_SQL = "update ethereum_transaction set gas_price = ?, "
            + "max_fee_per_gas = ?, max_priority_fee_per_gas = ?, "
            + "value = ? where consensus_timestamp = ?";

    private static final String UPDATE_V2_SQL = "update ethereum_transaction set gas_price = ?, "
            + "value = ? where consensus_timestamp = ? and payer_account_id = ?";

    private final ObjectProvider<EthereumTransactionParser> ethereumTransactionParserProvider;
    private final ObjectProvider<TransactionOperations> transactionOperationsProvider;
    private final boolean v2;

    ConvertEthereumTransactionToWeiBarMigration(
            DBProperties dbProperties,
            Environment environment,
            ImporterProperties importerProperties,
            ObjectProvider<JdbcOperations> jdbcOperationsProvider,
            ObjectProvider<EthereumTransactionParser> ethereumTransactionParserProvider,
            ObjectProvider<TransactionOperations> transactionOperationsProvider) {
        super(importerProperties.getMigration(), jdbcOperationsProvider, dbProperties.getSchema());
        this.v2 = environment.acceptsProfiles(Profiles.of("v2"));
        this.ethereumTransactionParserProvider = ethereumTransactionParserProvider;
        this.transactionOperationsProvider = transactionOperationsProvider;
    }

    @Override
    public String getDescription() {
        return "Convert ethereum transaction gas and value fields from tinybar to weibar by re-parsing RLP data";
    }

    @NonNull
    @Override
    protected Long getInitial() {
        return Long.MAX_VALUE;
    }

    private static final Map<Boolean, MigrationVersion> MINIMUM_VERSION = Map.of(
            Boolean.FALSE, MigrationVersion.fromVersion("1.119.0"),
            Boolean.TRUE, MigrationVersion.fromVersion("2.24.0"));

    @Override
    protected MigrationVersion getMinimumVersion() {
        return MINIMUM_VERSION.get(v2);
    }

    @Override
    public TransactionOperations getTransactionOperations() {
        return transactionOperationsProvider.getObject();
    }

    @NonNull
    @Override
    protected Optional<Long> migratePartial(Long lastConsensusTimestamp) {
        var params = Map.of("consensusTimestamp", lastConsensusTimestamp);
        var transactions = getNamedParameterJdbcOperations()
                .query(
                        SELECT_TRANSACTIONS_SQL,
                        params,
                        (rs, _) -> new TransactionRow(
                                rs.getLong("consensus_timestamp"),
                                rs.getBytes("data"),
                                rs.getLong("payer_account_id")));

        if (transactions.isEmpty()) {
            return Optional.empty();
        }

        var updates = new ArrayList<TransactionUpdate>(transactions.size());
        var failedCount = 0;
        var parser = ethereumTransactionParserProvider.getObject();

        for (var tx : transactions) {
            try {
                var parsed = parser.decode(tx.data());
                updates.add(new TransactionUpdate(
                        tx.consensusTimestamp(),
                        tx.payerAccountId(),
                        parsed.getGasPrice(),
                        parsed.getMaxFeePerGas(),
                        parsed.getMaxPriorityFeePerGas(),
                        parsed.getValue()));
            } catch (Exception e) {
                failedCount++;
                log.warn(
                        "Failed to decode ethereum transaction at consensus timestamp {}: {}",
                        tx.consensusTimestamp(),
                        e.getMessage());
            }
        }

        if (!updates.isEmpty()) {
            batchUpdate(updates);
        }

        if (failedCount > 0) {
            log.warn("Failed to decode {} ethereum transactions due to invalid RLP data", failedCount);
        }

        return Optional.of(transactions.getLast().consensusTimestamp());
    }

    private void batchUpdate(List<TransactionUpdate> updates) {
        var updateSql = v2 ? UPDATE_V2_SQL : UPDATE_SQL;
        getJdbcOperations().batchUpdate(updateSql, updates, updates.size(), (ps, update) -> {
            ps.setBytes(1, update.gasPrice());
            ps.setBytes(2, update.maxFeePerGas());
            ps.setBytes(3, update.maxPriorityFeePerGas());
            ps.setBytes(4, update.value());
            ps.setLong(5, update.consensusTimestamp());
            if (v2) {
                ps.setLong(6, update.payerAccountId());
            }
        });
    }

    private record TransactionRow(long consensusTimestamp, byte[] data, long payerAccountId) {}

    private record TransactionUpdate(
            long consensusTimestamp,
            long payerAccountId,
            byte[] gasPrice,
            byte[] maxFeePerGas,
            byte[] maxPriorityFeePerGas,
            byte[] value) {}
}
