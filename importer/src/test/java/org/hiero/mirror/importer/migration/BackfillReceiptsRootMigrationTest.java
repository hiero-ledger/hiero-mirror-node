// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.callback.SimpleContext;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.config.Owner;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.parser.record.receipt.ReceiptRoot;
import org.hiero.mirror.importer.repository.RecordFileRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcOperations;

@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
class BackfillReceiptsRootMigrationTest extends AbstractAsyncJavaMigrationTest<BackfillReceiptsRootMigration> {

    private static final String PROGRESS_TABLE = "backfill_receipts_root_progress_temp";
    private static final String PROGRESS_TABLE_EXISTS =
            "select exists(select from information_schema.tables where table_name = '" + PROGRESS_TABLE + "')";

    private final DBProperties dbProperties;
    private final Environment environment;
    private final EntityProperties entityProperties;

    @Owner
    private final ObjectProvider<JdbcOperations> jdbcOperationsProvider;

    private final BackfillReceiptsRootMigration migration;
    private final RecordFileRepository recordFileRepository;

    @Override
    protected BackfillReceiptsRootMigration getMigration() {
        return migration;
    }

    @Test
    void empty() {
        runMigration();
        waitForCompletion();
        assertThat(recordFileRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // Block 1: no EVM activity, missing receipts_root -> expect the empty root (32 zero bytes)
        var start1 = domainBuilder.timestamp();
        var end1 = start1 + 10;
        var block1 = persistBlockMissingReceiptsRoot(start1, end1);

        // Block 2: a contract call with a log and an ethereum transaction (type 2), missing receipts_root; the log's
        // contract has an evm address which must be resolved from the entity table
        var start2 = end1 + 1;
        var end2 = start2 + 10;
        var block2 = persistBlockMissingReceiptsRoot(start2, end2);
        var timestamp2 = start2 + 1;
        var contract = domainBuilder.entity().persist();
        var contractResult = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(timestamp2).transactionIndex(0))
                .persist();
        var contractLog = domainBuilder
                .contractLog()
                .customize(c -> c.consensusTimestamp(timestamp2)
                        .contractId(contract.toEntityId())
                        .index(0)
                        .transactionIndex(0))
                .persist();
        domainBuilder
                .ethereumTransaction(true)
                .customize(e -> e.consensusTimestamp(timestamp2).type(2))
                .persist();

        // Block 3: already has a receipts_root -> must be left untouched
        var start3 = end2 + 1;
        var end3 = start3 + 10;
        var existingRoot = domainBuilder.bytes(32);
        var block3 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start3).consensusEnd(end3).receiptsRoot(existingRoot))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        var ethereumTransaction = EthereumTransaction.builder().type(2).build();
        var expectedRootBlock2 = receiptRoot(Map.of(contract.getId(), contract.getEvmAddress()));
        expectedRootBlock2.put(contractResult, List.of(contractLog), ethereumTransaction);
        var expectedBlock2 = expectedRootBlock2.getRootHash();

        assertThat(recordFileRepository.findById(block1.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(new byte[32]);
        assertThat(recordFileRepository.findById(block2.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(expectedBlock2);
        assertThat(recordFileRepository.findById(block3.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(existingRoot);
    }

    @Test
    void migrateNullTransactionIndex() {
        // Rows with a null transaction_index hold no EVM transaction index slot (e.g. WRONG_NONCE ethereum
        // transactions or rows FixEvmTransactionIndexMigration nulled as non-EVM) and must not contribute receipts;
        // a block with only such rows gets the empty root.
        var start = domainBuilder.timestamp();
        var end = start + 10;
        var block = persistBlockMissingReceiptsRoot(start, end);

        var timestamp1 = start + 1;
        var timestamp2 = start + 2;
        var timestamp3 = start + 3;
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(timestamp1).transactionIndex(null))
                .persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(timestamp2).transactionIndex(null))
                .persist();
        domainBuilder
                .contractLog()
                .customize(c -> c.consensusTimestamp(timestamp3).index(0).transactionIndex(null))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(recordFileRepository.findById(block.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(new byte[32]);
    }

    @Test
    void migrateExcludesChildTransactions() {
        // A block with a top-level contract result and a child (internal) transaction that has both a contract result
        // and a log: the child's result and log must not contribute to the receipts root
        var start = domainBuilder.timestamp();
        var end = start + 10;
        var block = persistBlockMissingReceiptsRoot(start, end);

        var parentTimestamp = start + 1;
        var childTimestamp = start + 2;
        var parentResult = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(parentTimestamp).transactionIndex(0))
                .persist();
        domainBuilder
                .contractResult()
                .customize(c ->
                        c.consensusTimestamp(childTimestamp).transactionIndex(1).transactionNonce(1))
                .persist();
        domainBuilder
                .contractLog()
                .customize(c -> c.consensusTimestamp(childTimestamp).index(0).transactionIndex(1))
                .persist();

        // when
        runMigration();
        waitForCompletion();

        // then
        var expectedRoot = receiptRoot(Map.of());
        expectedRoot.put(parentResult, List.of(), null);
        var expected = expectedRoot.getRootHash();

        assertThat(recordFileRepository.findById(block.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(expected);
    }

    @Test
    void migrateIgnoresRowsOfBlocksAlreadyBackfilled() {
        // An already-backfilled block with EVM activity sits between two blocks missing the root. The batch's data
        // queries span its rows, but they must not leak into the neighboring blocks' roots.
        var start1 = domainBuilder.timestamp();
        var end1 = start1 + 10;
        var block1 = persistBlockMissingReceiptsRoot(start1, end1);

        var start2 = end1 + 1;
        var end2 = start2 + 10;
        var existingRoot = domainBuilder.bytes(32);
        var block2 = domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(start2).consensusEnd(end2).receiptsRoot(existingRoot))
                .persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(start2 + 1).transactionIndex(0))
                .persist();
        domainBuilder
                .contractLog()
                .customize(c -> c.consensusTimestamp(start2 + 1).index(0).transactionIndex(0))
                .persist();

        var start3 = end2 + 1;
        var end3 = start3 + 10;
        var block3 = persistBlockMissingReceiptsRoot(start3, end3);

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(recordFileRepository.findById(block1.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(new byte[32]);
        assertThat(recordFileRepository.findById(block2.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(existingRoot);
        assertThat(recordFileRepository.findById(block3.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(new byte[32]);
    }

    @Test
    void migrateMultipleBatches() {
        // More blocks than fit in a single batch to exercise the cursor advancing across iterations
        int blockCount = BackfillReceiptsRootMigration.DEFAULT_BATCH_SIZE + 1;
        var start = domainBuilder.timestamp();
        for (int i = 0; i < blockCount; i++) {
            persistBlockMissingReceiptsRoot(start, start + 9);
            start += 10;
        }

        // when
        runMigration();
        waitForCompletion();

        // then
        assertThat(recordFileRepository.findAll()).hasSize(blockCount).allSatisfy(recordFile -> assertThat(
                        recordFile.getReceiptsRoot())
                .isEqualTo(new byte[32]));
    }

    @Test
    void migrateWithCustomBatchSize() {
        // A batch size of 1 forces one iteration per block
        var start1 = domainBuilder.timestamp();
        var end1 = start1 + 10;
        var block1 = persistBlockMissingReceiptsRoot(start1, end1);
        var block2 = persistBlockMissingReceiptsRoot(end1 + 1, end1 + 11);

        // when
        runMigration(migrationWithBatchSize("1"));
        waitForCompletion();

        // then
        assertThat(recordFileRepository.findById(block1.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(new byte[32]);
        assertThat(recordFileRepository.findById(block2.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(new byte[32]);
    }

    @Test
    void migrateDropsProgressTableOnCompletion() {
        var start = domainBuilder.timestamp();
        var block = persistBlockMissingReceiptsRoot(start, start + 10);

        // when
        runMigration();
        waitForCompletion();

        // then the block is backfilled and the transient progress table is cleaned up
        assertThat(recordFileRepository.findById(block.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(new byte[32]);
        assertThat(progressTableExists()).isFalse();
    }

    @Test
    void migrateResumesFromProgressCheckpoint() {
        // A prior run backfilled down to block2 and checkpointed its consensus_end as the next upper bound. On resume
        // the migration must only process blocks strictly below the checkpoint, leaving block2 untouched.
        var start1 = domainBuilder.timestamp();
        var end1 = start1 + 10;
        var block1 = persistBlockMissingReceiptsRoot(start1, end1);

        var start2 = end1 + 1;
        var end2 = start2 + 10;
        var block2 = persistBlockMissingReceiptsRoot(start2, end2);

        seedProgressCheckpoint(block2.getConsensusEnd());

        // when
        runMigration();
        waitForCompletion();

        // then block1 (below the checkpoint) is backfilled while block2 (at the checkpoint) is skipped
        assertThat(recordFileRepository.findById(block1.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isEqualTo(new byte[32]);
        assertThat(recordFileRepository.findById(block2.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isNull();
        assertThat(progressTableExists()).isFalse();
    }

    @Test
    void migrateSkippedWhenContractResultsDisabled() {
        // Receipts roots are not computed at ingestion when contract result persistence is off, so the migration must
        // skip: leave the block untouched and not create the progress table.
        var start = domainBuilder.timestamp();
        var block = persistBlockMissingReceiptsRoot(start, start + 10);

        entityProperties.getPersist().setContractResults(false);
        try {
            runMigration();
            waitForCompletion();
        } finally {
            entityProperties.getPersist().setContractResults(true);
        }

        // then
        assertThat(recordFileRepository.findById(block.getConsensusEnd()))
                .get()
                .extracting(RecordFile::getReceiptsRoot)
                .isNull();
        assertThat(progressTableExists()).isFalse();
    }

    @Test
    void invalidBatchSize() {
        assertThatThrownBy(() -> migrationWithBatchSize("0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
        assertThatThrownBy(() -> migrationWithBatchSize("junk")).isInstanceOf(NumberFormatException.class);
    }

    @SneakyThrows
    private void runMigration(BackfillReceiptsRootMigration migration) {
        migration.doMigrate();
        migration.handle(Event.AFTER_MIGRATE_OPERATION_FINISH, new SimpleContext(new FluentConfiguration()));
    }

    private BackfillReceiptsRootMigration migrationWithBatchSize(String batchSize) {
        var migrationProperties = new MigrationProperties();
        migrationProperties.getParams().put("batchSize", batchSize);
        var importerProperties = new ImporterProperties();
        importerProperties.getMigration().put("backfillReceiptsRootMigration", migrationProperties);

        return new BackfillReceiptsRootMigration(
                dbProperties, environment, entityProperties, importerProperties, jdbcOperationsProvider);
    }

    private RecordFile persistBlockMissingReceiptsRoot(long consensusStart, long consensusEnd) {
        return domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(consensusStart)
                        .consensusEnd(consensusEnd)
                        .receiptsRoot(null))
                .persist();
    }

    private static ReceiptRoot receiptRoot(final Map<Long, byte[]> evmAddresses) {
        return new ReceiptRoot(evmAddresses);
    }

    private void seedProgressCheckpoint(long upperBound) {
        ownerJdbcTemplate.execute("create table if not exists " + PROGRESS_TABLE + "(upper_bound bigint not null)");
        ownerJdbcTemplate.update("insert into " + PROGRESS_TABLE + "(upper_bound) values (" + upperBound + ")");
    }

    private boolean progressTableExists() {
        return Boolean.TRUE.equals(ownerJdbcTemplate.queryForObject(PROGRESS_TABLE_EXISTS, Boolean.class));
    }
}
