// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.31.1")
class RemoveInvalidEntityMigrationTest extends ImporterIntegrationTest {

    @Value("classpath:db/migration/v1/V1.31.2__remove_invalid_entities.sql")
    private final File migrationSql;

    private final ImporterProperties importerProperties;
    private final TransactionRepository transactionRepository;

    @BeforeEach
    void before() {
        importerProperties.setStartDate(Instant.EPOCH);
        importerProperties.setEndDate(Instant.EPOCH.plusSeconds(1));
    }

    @Test
    void verifyEntityTypeMigrationEmpty() {
        // migration
        migrate();

        assertEquals(0, getEntityCount());
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void verifyEntityTypeMigrationValidEntities() {
        insertEntity(1, EntityType.ACCOUNT);
        insertEntity(2, EntityType.CONTRACT);
        insertEntity(3, EntityType.FILE);
        insertEntity(4, EntityType.TOPIC);
        insertEntity(5, EntityType.TOKEN);

        List<Transaction> transactionList = new ArrayList<>();
        transactionList.add(transaction(1, 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOCREATEACCOUNT));
        transactionList.add(transaction(20, 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTCREATEINSTANCE));
        transactionList.add(transaction(30, 3, ResponseCodeEnum.SUCCESS, TransactionType.FILECREATE));
        transactionList.add(transaction(40, 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSCREATETOPIC));
        transactionList.add(transaction(50, 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENCREATION));
        transactionList.forEach(this::insertTransaction);

        // migration
        migrate();

        assertEquals(5, getEntityCount());
        assertEquals(5, transactionRepository.count());
    }

    @Test
    void verifyEntityTypeMigrationInvalidEntities() {
        var typeMismatchedAccountEntityId = insertEntity(1, EntityType.TOPIC);
        var typeMismatchedContractEntityId = insertEntity(2, EntityType.TOKEN);
        var typeMismatchedFileEntityId = insertEntity(3, EntityType.CONTRACT);
        var typeMismatchedTopicEntityId = insertEntity(4, EntityType.ACCOUNT);
        var typeMismatchedTokenEntityId = insertEntity(5, EntityType.FILE);

        List<Transaction> transactionList = new ArrayList<>();
        transactionList.add(transaction(1, 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOCREATEACCOUNT));
        transactionList.add(transaction(20, 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTCREATEINSTANCE));
        transactionList.add(transaction(30, 3, ResponseCodeEnum.SUCCESS, TransactionType.FILECREATE));
        transactionList.add(transaction(40, 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSCREATETOPIC));
        transactionList.add(transaction(50, 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENCREATION));
        transactionList.add(
                transaction(70, 50, ResponseCodeEnum.INVALID_TOPIC_ID, TransactionType.CONSENSUSSUBMITMESSAGE));
        transactionList.add(
                transaction(80, 100, ResponseCodeEnum.TOPIC_EXPIRED, TransactionType.CONSENSUSSUBMITMESSAGE));
        transactionList.forEach(this::insertTransaction);

        // migration
        migrate();

        assertEquals(5, getEntityCount());
        assertEquals(7, transactionRepository.count());

        assertAll(
                () -> assertThat(findEntityById(typeMismatchedAccountEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.ACCOUNT),
                () -> assertThat(findEntityById(typeMismatchedContractEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.CONTRACT),
                () -> assertThat(findEntityById(typeMismatchedFileEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.FILE),
                () -> assertThat(findEntityById(typeMismatchedTopicEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.TOPIC),
                () -> assertThat(findEntityById(typeMismatchedTokenEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.TOKEN));
    }

    @Test
    void verifyEntityTypeMigrationInvalidEntitiesMultiBatch() {
        insertEntity(1, EntityType.ACCOUNT);
        insertEntity(2, EntityType.CONTRACT);
        insertEntity(3, EntityType.FILE);
        insertEntity(4, EntityType.TOPIC);
        insertEntity(5, EntityType.TOKEN);

        var typeMismatchedAccountEntityId = insertEntity(6, EntityType.TOPIC);
        var typeMismatchedContractEntityId = insertEntity(7, EntityType.TOKEN);
        var typeMismatchedFileEntityId = insertEntity(8, EntityType.CONTRACT);
        var typeMismatchedTopicEntityId = insertEntity(9, EntityType.ACCOUNT);
        var typeMismatchedTokenEntityId = insertEntity(10, EntityType.FILE);

        List<Transaction> transactionList = new ArrayList<>();
        transactionList.add(transaction(1, 1, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOCREATEACCOUNT));
        transactionList.add(transaction(20, 2, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTCREATEINSTANCE));
        transactionList.add(transaction(30, 3, ResponseCodeEnum.SUCCESS, TransactionType.FILECREATE));
        transactionList.add(transaction(40, 4, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSCREATETOPIC));
        transactionList.add(transaction(50, 5, ResponseCodeEnum.SUCCESS, TransactionType.TOKENCREATION));
        transactionList.add(transaction(60, 6, ResponseCodeEnum.SUCCESS, TransactionType.CRYPTOCREATEACCOUNT));
        transactionList.add(transaction(70, 7, ResponseCodeEnum.SUCCESS, TransactionType.CONTRACTCREATEINSTANCE));
        transactionList.add(transaction(80, 8, ResponseCodeEnum.SUCCESS, TransactionType.FILECREATE));
        transactionList.add(transaction(90, 9, ResponseCodeEnum.SUCCESS, TransactionType.CONSENSUSCREATETOPIC));
        transactionList.add(transaction(100, 10, ResponseCodeEnum.SUCCESS, TransactionType.TOKENCREATION));
        transactionList.add(
                transaction(500, 50, ResponseCodeEnum.INVALID_TOPIC_ID, TransactionType.CONSENSUSSUBMITMESSAGE));
        transactionList.add(
                transaction(1000, 100, ResponseCodeEnum.TOPIC_EXPIRED, TransactionType.CONSENSUSSUBMITMESSAGE));
        transactionList.forEach(this::insertTransaction);

        // migration
        migrate();

        assertEquals(10, getEntityCount());
        assertEquals(12, transactionRepository.count());

        assertAll(
                () -> assertThat(findEntityById(typeMismatchedAccountEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.ACCOUNT),
                () -> assertThat(findEntityById(typeMismatchedContractEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.CONTRACT),
                () -> assertThat(findEntityById(typeMismatchedFileEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.FILE),
                () -> assertThat(findEntityById(typeMismatchedTopicEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.TOPIC),
                () -> assertThat(findEntityById(typeMismatchedTokenEntityId.id()))
                        .extracting(MigrationEntity::type)
                        .isEqualTo(EntityType.TOKEN));
    }

    private EntityId getEntityId(long num) {
        return EntityId.of(0, 1, num);
    }

    private Transaction transaction(
            long consensusNs, long num, ResponseCodeEnum result, TransactionType transactionType) {
        Transaction transaction = new Transaction();
        transaction.setChargedTxFee(100L);
        transaction.setConsensusTimestamp(consensusNs);
        transaction.setEntityId(getEntityId(num));
        transaction.setInitialBalance(1000L);
        transaction.setMemo("transaction memo".getBytes());
        transaction.setNodeAccountId(getEntityId(3));
        transaction.setPayerAccountId(getEntityId(98));
        transaction.setResult(result.getNumber());
        transaction.setType(transactionType.getProtoId());
        transaction.setValidStartNs(20L);
        transaction.setValidDurationSeconds(11L);
        transaction.setMaxFee(33L);
        return transaction;
    }

    @SneakyThrows
    private void migrate() {
        ownerJdbcTemplate.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    /**
     * Insert transaction object using only columns supported in V_1_31_2
     *
     * @param transaction transaction domain
     */
    private void insertTransaction(Transaction transaction) {
        jdbcOperations.update(
                "insert into transaction (charged_tx_fee, entity_id, initial_balance, max_fee, memo, "
                        + "node_account_id, payer_account_id, result, transaction_bytes, "
                        + "transaction_hash, type, valid_duration_seconds, valid_start_ns, consensus_ns)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                transaction.getChargedTxFee(),
                transaction.getEntityId().getId(),
                transaction.getInitialBalance(),
                transaction.getMaxFee(),
                transaction.getMemo(),
                transaction.getNodeAccountId().getId(),
                transaction.getPayerAccountId().getId(),
                transaction.getResult(),
                transaction.getTransactionBytes(),
                transaction.getTransactionHash(),
                transaction.getType(),
                transaction.getValidDurationSeconds(),
                transaction.getValidStartNs(),
                transaction.getConsensusTimestamp());
    }

    /**
     * Insert entity object using only columns supported before V_1_36.2
     *
     * @param num long id
     * @param type EntityType
     */
    private MigrationEntity insertEntity(long num, EntityType type) {
        var entityId = getEntityId(num);
        var entity = MigrationEntity.builder()
                .autoRenewAccountId(EntityId.of("1.2.3").getId())
                .id(entityId.getId())
                .memo(DomainUtils.sanitize("abc" + (char) 0))
                .num(entityId.getNum())
                .proxyAccountId(EntityId.of("4.5.6").getId())
                .realm(entityId.getRealm())
                .shard(entityId.getShard())
                .type(type)
                .build();

        jdbcOperations.update(
                """
                    insert into t_entities (auto_renew_account_id, auto_renew_period, deleted, entity_num,
                      entity_realm, entity_shard, ed25519_public_key_hex, exp_time_ns, fk_entity_type_id,
                      id, key, memo, proxy_account_id, submit_key)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
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
        return entity;
    }

    private Integer getEntityCount() {
        return jdbcOperations.queryForObject("select count(*) from t_entities", Integer.class);
    }

    private MigrationEntity findEntityById(long id) {
        return jdbcOperations.queryForObject(
                "select * from t_entities where id = ?",
                (rs, rowNum) -> {
                    return MigrationEntity.builder()
                            .autoRenewAccountId(rs.getLong("auto_renew_account_id"))
                            .autoRenewPeriod(rs.getLong("auto_renew_period"))
                            .deleted(rs.getBoolean("deleted"))
                            .expirationTimestamp(rs.getLong("exp_time_ns"))
                            .id(rs.getLong("id"))
                            .key(rs.getBytes("key"))
                            .memo(rs.getString("memo"))
                            .num(rs.getLong("entity_num"))
                            .realm(rs.getLong("entity_realm"))
                            .shard(rs.getLong("entity_shard"))
                            .proxyAccountId(rs.getLong("proxy_account_id"))
                            .submitKey(rs.getBytes("submit_key"))
                            .type(EntityType.fromId(rs.getInt("fk_entity_type_id")))
                            .build();
                },
                id);
    }

    @Builder
    private record MigrationEntity(
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
