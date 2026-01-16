// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.File;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.35.5")
class CleanupEntityMigrationTest extends ImporterIntegrationTest {

    private static final RowMapper<PostMigrationEntity> MAPPER = new DataClassRowMapper<>(PostMigrationEntity.class);

    @Value("classpath:db/migration/v1/V1.36.2__entities_update.sql")
    private final File migrationSql;

    private final ImporterProperties importerProperties;
    private final TransactionRepository transactionRepository;

    @BeforeEach
    void before() {
        importerProperties.setStartDate(Instant.EPOCH);
        importerProperties.setEndDate(Instant.EPOCH.plusSeconds(1));
        setEntityTablesPreV_1_36();
    }

    @AfterEach
    void after() {
        cleanup();
    }

    @Test
    void verifyEntityTypeMigrationEmpty() {
        // migration
        migrate();

        assertThat(retrieveEntities()).isEmpty();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void verifyEntityMigrationCreationTransactions() {
        // given
        long[] ids = new long[] {1, 2, 3, 4, 5, 6};
        long[] createTimestamps = new long[] {10, 20, 30, 40, 50, 60};

        var expected = List.of(
                insertEntity(entity(false, ids[0], EntityType.ACCOUNT))
                        .createdTimestamp(createTimestamps[0])
                        .modifiedTimestamp(createTimestamps[0])
                        .build(),
                insertEntity(entity(false, ids[1], EntityType.CONTRACT))
                        .createdTimestamp(createTimestamps[1])
                        .modifiedTimestamp(createTimestamps[1])
                        .build(),
                insertEntity(entity(false, ids[2], EntityType.FILE))
                        .createdTimestamp(createTimestamps[2])
                        .modifiedTimestamp(createTimestamps[2])
                        .build(),
                insertEntity(entity(false, ids[3], EntityType.TOPIC))
                        .createdTimestamp(createTimestamps[3])
                        .modifiedTimestamp(createTimestamps[3])
                        .build(),
                insertEntity(entity(false, ids[4], EntityType.TOKEN))
                        .createdTimestamp(createTimestamps[4])
                        .modifiedTimestamp(createTimestamps[4])
                        .build(),
                insertEntity(entity(false, ids[5], EntityType.SCHEDULE))
                        .createdTimestamp(createTimestamps[5])
                        .modifiedTimestamp(createTimestamps[5])
                        .build());

        insertTransaction(createTimestamps[0], 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOCREATEACCOUNT);
        insertTransaction(createTimestamps[1], 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTCREATEINSTANCE);
        insertTransaction(createTimestamps[2], 3, ResponseCodeEnum.SUCCESS, TransactionType.FILECREATE);
        insertTransaction(createTimestamps[3], 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSCREATETOPIC);
        insertTransaction(createTimestamps[4], 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENCREATION);
        insertTransaction(createTimestamps[5], 6, ResponseCodeEnum.SUCCESS, TransactionType.SCHEDULECREATE);

        assertThat(getEntitiesCount()).isEqualTo(ids.length);

        // migration
        migrate();

        // then
        assertThat(retrieveEntities()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void verifyEntityMigrationCreationTransactionsWithFailures() {
        long[] ids = new long[] {1, 2, 3, 4, 5, 6};
        long[] createTimestamps = new long[] {10, 20, 30, 40, 50, 60};

        var expected = List.of(
                insertEntity(entity(false, ids[0], EntityType.ACCOUNT))
                        .createdTimestamp(createTimestamps[0])
                        .modifiedTimestamp(createTimestamps[0])
                        .build(),
                insertEntity(entity(false, ids[1], EntityType.CONTRACT))
                        .createdTimestamp(createTimestamps[1])
                        .modifiedTimestamp(createTimestamps[1])
                        .build(),
                insertEntity(entity(false, ids[2], EntityType.FILE))
                        .createdTimestamp(createTimestamps[2])
                        .modifiedTimestamp(createTimestamps[2])
                        .build(),
                insertEntity(entity(false, ids[3], EntityType.TOPIC))
                        .createdTimestamp(createTimestamps[3])
                        .modifiedTimestamp(createTimestamps[3])
                        .build(),
                insertEntity(entity(false, ids[4], EntityType.TOKEN))
                        .createdTimestamp(createTimestamps[4])
                        .modifiedTimestamp(createTimestamps[4])
                        .build(),
                insertEntity(entity(false, ids[5], EntityType.SCHEDULE))
                        .createdTimestamp(createTimestamps[5])
                        .modifiedTimestamp(createTimestamps[5])
                        .build());

        // failed create transactions
        insertTransaction(
                createTimestamps[0] - 1,
                1,
                ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE,
                TransactionType.CRYPTOCREATEACCOUNT);
        insertTransaction(
                createTimestamps[1] - 1,
                2,
                ResponseCodeEnum.INVALID_TRANSACTION,
                TransactionType.CONTRACTCREATEINSTANCE);
        insertTransaction(
                createTimestamps[2] - 1, 3, ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND, TransactionType.FILECREATE);
        insertTransaction(
                createTimestamps[3] - 1,
                4,
                ResponseCodeEnum.INVALID_NODE_ACCOUNT,
                TransactionType.CONSENSUSCREATETOPIC);
        insertTransaction(
                createTimestamps[4] - 1, 5, ResponseCodeEnum.INVALID_SIGNATURE, TransactionType.TOKENCREATION);
        insertTransaction(createTimestamps[5] - 1, 6, ResponseCodeEnum.MEMO_TOO_LONG, TransactionType.SCHEDULECREATE);

        // successful create transactions
        insertTransaction(createTimestamps[0], 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOCREATEACCOUNT);
        insertTransaction(createTimestamps[1], 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTCREATEINSTANCE);
        insertTransaction(createTimestamps[2], 3, ResponseCodeEnum.SUCCESS, TransactionType.FILECREATE);
        insertTransaction(createTimestamps[3], 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSCREATETOPIC);
        insertTransaction(createTimestamps[4], 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENCREATION);
        insertTransaction(createTimestamps[5], 6, ResponseCodeEnum.SUCCESS, TransactionType.SCHEDULECREATE);

        // migration
        migrate();

        // then
        assertThat(retrieveEntities()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void verifyEntityMigrationWithSingleUpdate() {
        // excludes schedules as they can't yet be updated
        long[] ids = new long[] {1, 2, 3, 4, 5};
        long[] createTimestamps = new long[] {10, 20, 30, 40, 50};
        long[] modifiedTimestamps = new long[] {110, 120, 130, 140, 150};

        var expected = List.of(
                insertEntity(entity(false, ids[0], EntityType.ACCOUNT))
                        .createdTimestamp(createTimestamps[0])
                        .modifiedTimestamp(modifiedTimestamps[0])
                        .build(),
                insertEntity(entity(false, ids[1], EntityType.CONTRACT))
                        .createdTimestamp(createTimestamps[1])
                        .modifiedTimestamp(modifiedTimestamps[1])
                        .build(),
                insertEntity(entity(false, ids[2], EntityType.FILE))
                        .createdTimestamp(createTimestamps[2])
                        .modifiedTimestamp(modifiedTimestamps[2])
                        .build(),
                insertEntity(entity(false, ids[3], EntityType.TOPIC))
                        .createdTimestamp(createTimestamps[3])
                        .modifiedTimestamp(modifiedTimestamps[3])
                        .build(),
                insertEntity(entity(false, ids[4], EntityType.TOKEN))
                        .createdTimestamp(createTimestamps[4])
                        .modifiedTimestamp(modifiedTimestamps[4])
                        .build());

        // successful create transactions
        insertTransaction(createTimestamps[0], 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOCREATEACCOUNT);
        insertTransaction(createTimestamps[1], 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTCREATEINSTANCE);
        insertTransaction(createTimestamps[2], 3, ResponseCodeEnum.SUCCESS, TransactionType.FILECREATE);
        insertTransaction(createTimestamps[3], 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSCREATETOPIC);
        insertTransaction(createTimestamps[4], 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENCREATION);

        // successful update transactions
        insertTransaction(modifiedTimestamps[0], 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOUPDATEACCOUNT);
        insertTransaction(modifiedTimestamps[1], 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTUPDATEINSTANCE);
        insertTransaction(modifiedTimestamps[2], 3, ResponseCodeEnum.SUCCESS, TransactionType.FILEUPDATE);
        insertTransaction(modifiedTimestamps[3], 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSUPDATETOPIC);
        insertTransaction(modifiedTimestamps[4], 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENUPDATE);

        // migration
        migrate();

        // then
        assertThat(retrieveEntities()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void verifyEntityMigrationWithMultipleUpdates() {
        // excludes schedules as they can't yet be updated
        long[] ids = new long[] {1, 2, 3, 4, 5};
        long[] createTimestamps = new long[] {10, 20, 30, 40, 50};
        long[] deletedTimestamps = new long[] {210, 220, 230, 240, 250};

        var expected = List.of(
                insertEntity(entity(true, ids[0], EntityType.ACCOUNT))
                        .createdTimestamp(createTimestamps[0])
                        .modifiedTimestamp(deletedTimestamps[0])
                        .build(),
                insertEntity(entity(true, ids[1], EntityType.CONTRACT))
                        .createdTimestamp(createTimestamps[1])
                        .modifiedTimestamp(deletedTimestamps[1])
                        .build(),
                insertEntity(entity(true, ids[2], EntityType.FILE))
                        .createdTimestamp(createTimestamps[2])
                        .modifiedTimestamp(deletedTimestamps[2])
                        .build(),
                insertEntity(entity(true, ids[3], EntityType.TOPIC))
                        .createdTimestamp(createTimestamps[3])
                        .modifiedTimestamp(deletedTimestamps[3])
                        .build(),
                insertEntity(entity(true, ids[4], EntityType.TOKEN))
                        .createdTimestamp(createTimestamps[4])
                        .modifiedTimestamp(deletedTimestamps[4])
                        .build());

        // successful create transactions
        insertTransaction(createTimestamps[0], 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOCREATEACCOUNT);
        insertTransaction(createTimestamps[1], 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTCREATEINSTANCE);
        insertTransaction(createTimestamps[2], 3, ResponseCodeEnum.SUCCESS, TransactionType.FILECREATE);
        insertTransaction(createTimestamps[3], 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSCREATETOPIC);
        insertTransaction(createTimestamps[4], 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENCREATION);

        // successful update transactions
        long[] modifiedTimestamps = new long[] {110, 120, 130, 140, 150};
        insertTransaction(modifiedTimestamps[0], 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOUPDATEACCOUNT);
        insertTransaction(modifiedTimestamps[1], 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTUPDATEINSTANCE);
        insertTransaction(modifiedTimestamps[2], 3, ResponseCodeEnum.SUCCESS, TransactionType.FILEUPDATE);
        insertTransaction(modifiedTimestamps[3], 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSUPDATETOPIC);
        insertTransaction(modifiedTimestamps[4], 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENUPDATE);

        insertTransaction(deletedTimestamps[0], 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTODELETE);
        insertTransaction(deletedTimestamps[1], 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTDELETEINSTANCE);
        insertTransaction(deletedTimestamps[2], 3, ResponseCodeEnum.SUCCESS, TransactionType.FILEDELETE);
        insertTransaction(deletedTimestamps[3], 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSDELETETOPIC);
        insertTransaction(deletedTimestamps[4], 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENDELETION);

        // migration
        migrate();

        // then
        assertThat(retrieveEntities()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private Transaction transaction(
            long consensusNs, long entityNum, ResponseCodeEnum result, TransactionType transactionType) {
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(100L);
        transaction.setConsensusTimestamp(consensusNs);
        transaction.setEntityId(EntityId.of(0, 0, entityNum));
        transaction.setInitialBalance(1000L);
        transaction.setMemo("transaction memo".getBytes());
        transaction.setNodeAccountId(EntityId.of(0, 1, 3));
        transaction.setPayerAccountId(EntityId.of(0, 1, 98));
        transaction.setResult(result.getNumber());
        transaction.setType(transactionType.getProtoId());
        transaction.setValidStartNs(20L);
        transaction.setValidDurationSeconds(11L);
        transaction.setMaxFee(33L);
        return transaction;
    }

    private void insertTransaction(
            long consensusTimestamp, long entityNum, ResponseCodeEnum result, TransactionType transactionType) {
        Transaction transaction = transaction(consensusTimestamp, entityNum, result, transactionType);
        jdbcOperations.update(
                "insert into transaction (charged_tx_fee, consensus_ns, entity_id, initial_balance, max_fee, "
                        + "memo, "
                        + "node_account_id, payer_account_id, result, scheduled, transaction_bytes, "
                        + "transaction_hash, type, valid_duration_seconds, valid_start_ns)"
                        + " values"
                        + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                transaction.getChargedTxFee(),
                transaction.getConsensusTimestamp(),
                transaction.getEntityId().getId(),
                transaction.getInitialBalance(),
                transaction.getMaxFee(),
                transaction.getMemo(),
                transaction.getNodeAccountId().getId(),
                transaction.getPayerAccountId().getId(),
                transaction.getResult(),
                transaction.isScheduled(),
                transaction.getTransactionBytes(),
                transaction.getTransactionHash(),
                transaction.getType(),
                transaction.getValidDurationSeconds(),
                transaction.getValidStartNs());
    }

    private PostMigrationEntity.PostMigrationEntityBuilder postMigrationEntity(PreMigrationEntity entity) {
        return PostMigrationEntity.builder()
                .autoRenewAccountId(entity.autoRenewAccountId())
                .autoRenewPeriod(entity.autoRenewPeriod())
                .deleted(entity.deleted())
                .expirationTimestamp(entity.expirationTimestamp())
                .id(entity.id())
                .key(entity.key())
                .memo(entity.memo())
                .num(entity.num())
                .publicKey(entity.publicKey())
                .proxyAccountId(entity.proxyAccountId())
                .realm(entity.realm())
                .shard(entity.shard())
                .type(entity.type());
    }

    private PreMigrationEntity entity(boolean deleted, long id, EntityType entityType) {
        return PreMigrationEntity.builder()
                .autoRenewAccountId(EntityId.of("1.2.3").getId())
                .deleted(deleted)
                .id(id)
                .memo(domainBuilder.text(4))
                .num(id)
                .proxyAccountId(EntityId.of("4.5.6").getId())
                .realm(0L)
                .shard(0L)
                .type(entityType)
                .build();
    }

    @SneakyThrows
    private void migrate() {
        ownerJdbcTemplate.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    /**
     * Insert entity object using only columns supported before V_1_36.2
     *
     * @param entity entity domain
     */
    private PostMigrationEntity.PostMigrationEntityBuilder insertEntity(PreMigrationEntity entity) {
        jdbcOperations.update(
                "insert into t_entities (auto_renew_account_id, auto_renew_period, deleted, entity_num, "
                        + "entity_realm, entity_shard, ed25519_public_key_hex, exp_time_ns, fk_entity_type_id, "
                        + "id, key, memo, proxy_account_id, submit_key) values"
                        + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entity.autoRenewAccountId(),
                entity.autoRenewPeriod(),
                entity.deleted(),
                entity.num(),
                entity.realm(),
                entity.shard(),
                entity.publicKey(),
                entity.expirationTimestamp(),
                entity.type().getId(),
                entity.id(),
                entity.key(),
                entity.memo(),
                entity.proxyAccountId(),
                entity.submitKey());
        return postMigrationEntity(entity);
    }

    private List<PostMigrationEntity> retrieveEntities() {
        return jdbcOperations.query("select * from entity", MAPPER);
    }

    /**
     * Ensure entity tables match expected state before V_1_36.2
     */
    private void setEntityTablesPreV_1_36() {
        // remove entity table if present
        ownerJdbcTemplate.execute("drop table if exists entity cascade;");

        // add t_entities if not present
        ownerJdbcTemplate.execute("""
    create table if not exists t_entities (
        entity_num             bigint  not null,
        entity_realm           bigint  not null,
        entity_shard           bigint  not null,
        fk_entity_type_id      integer not null,
        auto_renew_period      bigint,
        key                    bytea,
        deleted                boolean default false,
        exp_time_ns            bigint,
        ed25519_public_key_hex character varying,
        submit_key             bytea,
        memo                   text,
        auto_renew_account_id  bigint,
        id                     bigint  not null,
        proxy_account_id       bigint
    );
""");
    }

    private Integer getEntitiesCount() {
        return jdbcOperations.queryForObject("select count(*) from t_entities", Integer.class);
    }

    private void cleanup() {
        ownerJdbcTemplate.execute("""
                                    truncate table entity restart identity cascade;
                                    drop table if exists t_entities cascade;
                                    """);
    }

    @Builder
    private record PostMigrationEntity(
            Long autoRenewAccountId,
            Long autoRenewPeriod,
            Long createdTimestamp,
            Boolean deleted,
            Long expirationTimestamp,
            Long id,
            byte[] key,
            String memo,
            Long modifiedTimestamp,
            Long num,
            Long proxyAccountId,
            String publicKey,
            Long realm,
            Long shard,
            byte[] submitKey,
            EntityType type) {}

    @Builder
    private record PreMigrationEntity(
            Long autoRenewAccountId,
            Long autoRenewPeriod,
            Boolean deleted,
            Long expirationTimestamp,
            Long id,
            byte[] key,
            String memo,
            Long num,
            Long proxyAccountId,
            String publicKey,
            Long realm,
            Long shard,
            byte[] submitKey,
            EntityType type) {}
}
