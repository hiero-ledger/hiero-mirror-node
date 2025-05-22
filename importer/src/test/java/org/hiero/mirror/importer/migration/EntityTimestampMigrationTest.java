// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.annotation.Resource;
import java.io.File;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.repository.TransactionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.39.2")
class EntityTimestampMigrationTest extends ImporterIntegrationTest {

    private static final EntityId NODE_ACCOUNT_ID = EntityId.of(0, 0, 3);
    private static final EntityId PAYER_ID = EntityId.of(0, 0, 10001);

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.40.0__entity_timestamp.sql")
    private File migrationSql;

    @Resource
    private TransactionRepository transactionRepository;

    @Test
    void verifyEntityTimestampMigrationEmpty() throws Exception {
        // migration
        migrate();

        assertThat(jdbcOperations.queryForObject("select count(*) from entity", Integer.class))
                .isZero();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void verifyEntityTimestampMigration() throws Exception {
        // given
        persistEntities(List.of(
                entity(9000, EntityType.ACCOUNT, 99L, 99L),
                entity(9001, EntityType.ACCOUNT, 100L, 101L),
                entity(9002, EntityType.CONTRACT),
                entity(9003, EntityType.FILE),
                entity(9004, EntityType.TOPIC),
                entity(9005, EntityType.TOKEN),
                entity(9006, EntityType.SCHEDULE)));

        persistTransactions(List.of(
                transaction(102L, 9001, SUCCESS, TransactionType.CRYPTOUPDATEACCOUNT),
                transaction(103L, 9002, SUCCESS, TransactionType.CONTRACTCREATEINSTANCE),
                transaction(104L, 9002, INSUFFICIENT_TX_FEE, TransactionType.CONTRACTUPDATEINSTANCE),
                transaction(105L, 9003, SUCCESS, TransactionType.FILECREATE),
                transaction(106L, 9003, SUCCESS, TransactionType.FILEDELETE),
                transaction(107L, 9004, SUCCESS, TransactionType.CONSENSUSCREATETOPIC),
                transaction(108L, 9004, SUCCESS, TransactionType.CONSENSUSUPDATETOPIC),
                transaction(109L, 9004, SUCCESS, TransactionType.CONSENSUSUPDATETOPIC),
                transaction(110L, 9004, SUCCESS, TransactionType.CONSENSUSUPDATETOPIC),
                transaction(111L, 9005, SUCCESS, TransactionType.TOKENCREATION),
                transaction(112L, 9006, SUCCESS, TransactionType.SCHEDULECREATE)));

        List<MigrationEntity> expected = List.of(
                entity(9000, EntityType.ACCOUNT, 99L, 99L), // no change
                entity(9001, EntityType.ACCOUNT, 100L, 102L), // updated at 102L
                entity(9002, EntityType.CONTRACT, 103L, 103L), // update transaction failed at 104L
                entity(9003, EntityType.FILE, 105L, 106L), // created at 105L, deleted at 106L
                entity(9004, EntityType.TOPIC, 107L, 110L), // last update at 110L
                entity(9005, EntityType.TOKEN, 111L, 111L),
                entity(9006, EntityType.SCHEDULE, 112L, 112L));

        // when
        migrate();

        // then
        assertThat(retrieveEntities())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "createdTimestamp", "modifiedTimestamp")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private MigrationEntity entity(long id, EntityType entityType) {
        return entity(id, entityType, null, null);
    }

    private MigrationEntity entity(long id, EntityType entityType, Long createdTimestamp, Long modifiedTimestamp) {
        MigrationEntity entity = new MigrationEntity();
        entity.setCreatedTimestamp(createdTimestamp);
        entity.setId(id);
        entity.setModifiedTimestamp(modifiedTimestamp);
        entity.setNum(id);
        entity.setType(entityType.getId());
        return entity;
    }

    private Transaction transaction(long consensusNs, long entityNum, ResponseCodeEnum result, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setConsensusTimestamp(consensusNs);
        transaction.setEntityId(EntityId.of(0, 0, entityNum));
        transaction.setNodeAccountId(NODE_ACCOUNT_ID);
        transaction.setPayerAccountId(PAYER_ID);
        transaction.setResult(result.getNumber());
        transaction.setType(type.getProtoId());
        transaction.setValidStartNs(consensusNs - 10);
        return transaction;
    }

    private void migrate() throws Exception {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private void persistEntities(List<MigrationEntity> entities) {
        for (MigrationEntity entity : entities) {
            jdbcOperations.update(
                    "insert into entity (created_timestamp, id, modified_timestamp, num, realm, shard, type) "
                            + "values (?,?,?,?,?,?,?)",
                    entity.getCreatedTimestamp(),
                    entity.getId(),
                    entity.getModifiedTimestamp(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    entity.getType());
        }
    }

    private List<MigrationEntity> retrieveEntities() {
        return jdbcOperations.query("select * from entity", new BeanPropertyRowMapper<>(MigrationEntity.class));
    }

    private void persistTransactions(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            jdbcOperations.update(
                    "insert into transaction (charged_tx_fee, consensus_ns, entity_id, initial_balance, " + "max_fee, "
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
    }

    // Use a custom class for Entity table since its columns have changed from the current domain object
    @Data
    @NoArgsConstructor
    private static class MigrationEntity {
        private Long createdTimestamp;
        private long id;
        private Long modifiedTimestamp;
        private long num;
        private long realm = 0;
        private long shard = 0;
        private int type;
    }
}
