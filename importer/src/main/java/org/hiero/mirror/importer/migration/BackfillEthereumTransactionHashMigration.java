// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.hiero.mirror.common.domain.transaction.TransactionType.ETHEREUMTRANSACTION;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import org.apache.commons.lang3.ArrayUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.TransactionHash;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Named
public class BackfillEthereumTransactionHashMigration extends RepeatableMigration {

    private static final String INSERT_TRANSACTION_HASH_SQL =
            """
            insert into transaction_hash (consensus_timestamp, distribution_id, hash, payer_account_id)
            values (?, ?, ?, ?)
            """;
    private static final ParameterizedPreparedStatementSetter<MigrationEthereumTransaction> PSS = (ps, transaction) -> {
        ps.setBytes(1, transaction.getHash());
        ps.setLong(2, transaction.getConsensusTimestamp());
    };
    private static final RowMapper<MigrationEthereumTransaction> ROW_MAPPER =
            new DataClassRowMapper<>(MigrationEthereumTransaction.class);
    private static final String SELECT_ETHEREUM_TRANSACTION_SQL =
            """
            select call_data, call_data_id, consensus_timestamp, data, hash, payer_account_id
            from ethereum_transaction
            where hash = ''::bytea and consensus_timestamp > ?
            order by consensus_timestamp
            limit 200
            """;
    private static final String UPDATE_CONTRACT_RESULT_SQL =
            """
            update contract_result
            set transaction_hash = ?
            where consensus_timestamp = ?
            """;
    // Workaround for citus as changing the value of distribution column contract_transaction_hash.hash is not allowed
    private static final String UPDATE_CONTRACT_TRANSACTION_HASH_SQL =
            """
            with deleted as (
              delete from contract_transaction_hash
              where consensus_timestamp = ? and hash = ''::bytea
              returning *
            )
            insert into contract_transaction_hash (consensus_timestamp, entity_id, hash, payer_account_id, transaction_result)
            select consensus_timestamp, entity_id, ?, payer_account_id, transaction_result
            from deleted
            """;
    private static final String UPDATE_ETHEREUM_HASH_SQL =
            """
            update ethereum_transaction
            set hash = ?
            where consensus_timestamp = ?
            """;

    private final EntityProperties entityProperties;
    private final ObjectProvider<EthereumTransactionParser> ethereumTransactionParserProvider;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public BackfillEthereumTransactionHashMigration(
            EntityProperties entityProperties,
            ObjectProvider<EthereumTransactionParser> ethereumTransactionParserProvider,
            ImporterProperties importerProperties,
            @Owner ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        super(importerProperties.getMigration());
        this.entityProperties = entityProperties;
        this.ethereumTransactionParserProvider = ethereumTransactionParserProvider;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public String getDescription() {
        return "Backfill ethereum transaction hash if it's empty";
    }

    @Override
    protected void doMigrate() throws IOException {
        var found = new AtomicInteger();
        var patched = new AtomicInteger();
        var stopwatch = Stopwatch.createStarted();

        getTransactionOperations().executeWithoutResult(s -> {
            long consensusTimestamp = -1;
            for (; ; ) {
                var transactions = jdbcTemplateProvider
                        .getObject()
                        .query(SELECT_ETHEREUM_TRANSACTION_SQL, ROW_MAPPER, consensusTimestamp);
                if (transactions.isEmpty()) {
                    break;
                }

                found.addAndGet(transactions.size());
                consensusTimestamp = transactions.getLast().getConsensusTimestamp();
                var patchedTransactions = transactions.stream()
                        .map(t -> {
                            var callDataId = t.getCallDataId() == null ? null : EntityId.of(t.getCallDataId());
                            t.setHash(ethereumTransactionParserProvider
                                    .getObject()
                                    .getHash(t.getCallData(), callDataId, t.getConsensusTimestamp(), t.getData()));
                            return t;
                        })
                        .filter(t -> ArrayUtils.isNotEmpty(t.getHash()))
                        .toList();
                patched.addAndGet(patchedTransactions.size());
                backfillTables(patchedTransactions);
            }
        });

        log.info("Backfilled hash for {} out of {} ethereum transactions in {}", patched, found, stopwatch);
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // Version in which transaction_hash.distribution_id is added
        return MigrationVersion.fromVersion("1.99.1");
    }

    private void backfillTables(List<MigrationEthereumTransaction> patchedTransactions) {
        if (patchedTransactions.isEmpty()) {
            return;
        }

        jdbcTemplateProvider
                .getObject()
                .batchUpdate(UPDATE_CONTRACT_RESULT_SQL, patchedTransactions, patchedTransactions.size(), PSS);

        jdbcTemplateProvider
                .getObject()
                .batchUpdate(
                        UPDATE_CONTRACT_TRANSACTION_HASH_SQL,
                        patchedTransactions,
                        patchedTransactions.size(),
                        (ps, transaction) -> {
                            ps.setLong(1, transaction.getConsensusTimestamp());
                            ps.setBytes(2, transaction.getHash());
                        });

        jdbcTemplateProvider
                .getObject()
                .batchUpdate(UPDATE_ETHEREUM_HASH_SQL, patchedTransactions, patchedTransactions.size(), PSS);

        if (entityProperties.getPersist().shouldPersistTransactionHash(ETHEREUMTRANSACTION)) {
            var transactionHashes = patchedTransactions.stream()
                    .map(t -> TransactionHash.builder()
                            .consensusTimestamp(t.getConsensusTimestamp())
                            .hash(t.getHash())
                            .payerAccountId(t.getPayerAccountId())
                            .build())
                    .toList();
            jdbcTemplateProvider
                    .getObject()
                    .batchUpdate(
                            INSERT_TRANSACTION_HASH_SQL,
                            transactionHashes,
                            transactionHashes.size(),
                            (ps, transactionHash) -> {
                                ps.setLong(1, transactionHash.getConsensusTimestamp());
                                ps.setShort(2, transactionHash.getDistributionId());
                                ps.setBytes(3, transactionHash.getHash());
                                ps.setLong(4, transactionHash.getPayerAccountId());
                            });
        }
    }

    private TransactionOperations getTransactionOperations() {
        var transactionManager = new DataSourceTransactionManager(
                Objects.requireNonNull(jdbcTemplateProvider.getObject().getDataSource()));
        return new TransactionTemplate(transactionManager);
    }

    @Data
    private static class MigrationEthereumTransaction {
        private byte[] callData;
        private Long callDataId;
        private long consensusTimestamp;
        private byte[] data;
        private byte[] hash;
        private long payerAccountId;
    }
}
