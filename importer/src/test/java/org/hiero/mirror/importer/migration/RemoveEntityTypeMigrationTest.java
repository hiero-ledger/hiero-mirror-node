// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.domain.entity.EntityType.FILE;
import static org.hiero.mirror.common.domain.entity.EntityType.SCHEDULE;
import static org.hiero.mirror.common.domain.entity.EntityType.TOKEN;
import static org.hiero.mirror.common.domain.entity.EntityType.TOPIC;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.converter.RangeToStringSerializer;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.46.11")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RemoveEntityTypeMigrationTest extends ImporterIntegrationTest {

    @Value("classpath:db/migration/v1/V1.47.1__remove_t_entity_types.sql")
    private File migrationSql;

    @Test
    void verify() {
        // given
        List<MigrationEntityV1_47_1> entities = new ArrayList<>();
        List<MigrationContractV1_47_1> contracts = new ArrayList<>();
        List<MigrationEntityV1_47_1> badEntities = Arrays.asList(entity(1, 1, CONTRACT));
        List<MigrationContractV1_47_1> badContracts = Arrays.asList(contract(1, 1, FILE));

        // Entities can have any type except CONTRACT
        entities.add(entity(1, 1, ACCOUNT));
        entities.add(entity(2, 2, FILE));
        entities.add(entity(3, 3, TOPIC));
        entities.add(entity(4, 4, TOKEN));
        entities.add(entity(5, 5, SCHEDULE));

        // Contracts can only have type CONTRACT
        contracts.add(contract(6, 6, CONTRACT));

        persistEntities(entities, false);
        persistEntitiesHistory(entities, false);
        persistContracts(contracts, false);
        persistContractsHistory(contracts, false);
        // when
        migrate();

        // then
        assertThat(findAllEntities())
                .extracting(MigrationEntityV1_47_1::getType)
                .containsExactly(ACCOUNT, FILE, TOPIC, TOKEN, SCHEDULE);
        assertThat(findAllEntitiesHistory())
                .extracting(MigrationEntityV1_47_1::getType)
                .containsExactly(ACCOUNT, FILE, TOPIC, TOKEN, SCHEDULE);
        assertThat(findAllContracts())
                .extracting(MigrationContractV1_47_1::getType)
                .containsExactly(CONTRACT);
        assertThat(findAllContractsHistory())
                .extracting(MigrationContractV1_47_1::getType)
                .containsExactly(CONTRACT);

        assertThrows(DataIntegrityViolationException.class, () -> {
            persistEntities(badEntities, true);
        });
        assertThrows(DataIntegrityViolationException.class, () -> {
            persistEntitiesHistory(badEntities, true);
        });
        assertThrows(DataIntegrityViolationException.class, () -> {
            persistContracts(badContracts, true);
        });
        assertThrows(DataIntegrityViolationException.class, () -> {
            persistContractsHistory(badContracts, true);
        });
    }

    @Test
    void verifyEmpty() {
        // given

        // when
        migrate();

        // then
        assertThat(findAllEntities()).isEmpty();
        assertThat(findAllEntitiesHistory()).isEmpty();
        assertThat(findAllContracts()).isEmpty();
        assertThat(findAllContractsHistory()).isEmpty();
    }

    @SneakyThrows
    private void migrate() {
        ownerJdbcTemplate.execute(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private MigrationEntityV1_47_1 entity(long id, long num, EntityType type) {
        MigrationEntityV1_47_1 entity = new MigrationEntityV1_47_1();
        entity.setId(id);
        entity.setNum(num);
        entity.setType(type);
        return entity;
    }

    private MigrationContractV1_47_1 contract(long id, long num, EntityType type) {
        MigrationContractV1_47_1 entity = new MigrationContractV1_47_1();
        entity.setId(id);
        entity.setNum(num);
        entity.setType(type);
        return entity;
    }

    private void persistEntities(List<MigrationEntityV1_47_1> entities, boolean migrationRan) {
        for (MigrationEntityV1_47_1 entity : entities) {
            jdbcOperations.update(
                    getEntitySql("entity", migrationRan),
                    entity.getId(),
                    entity.getCreatedTimestamp(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    migrationRan
                            ? entity.getType().toString()
                            : entity.getType().getId(),
                    String.format(
                            "(%d, %d)",
                            entity.getCreatedTimestamp(),
                            entity.getTimestampRange().lowerEndpoint()));
        }
    }

    private void persistEntitiesHistory(List<MigrationEntityV1_47_1> entities, boolean migrationRan) {
        for (MigrationEntityV1_47_1 entity : entities) {
            jdbcOperations.update(
                    getEntitySql("entity_history", migrationRan),
                    entity.getId(),
                    entity.getCreatedTimestamp(),
                    entity.getNum(),
                    entity.getRealm(),
                    entity.getShard(),
                    migrationRan
                            ? entity.getType().toString()
                            : entity.getType().getId(),
                    String.format(
                            "(%d, %d)",
                            entity.getCreatedTimestamp(),
                            entity.getTimestampRange().lowerEndpoint()));
        }
    }

    private void persistContracts(List<MigrationContractV1_47_1> contracts, boolean migrationRan) {
        for (MigrationContractV1_47_1 contract : contracts) {
            jdbcOperations.update(
                    getContractSql("contract", migrationRan),
                    contract.getId(),
                    contract.getCreatedTimestamp(),
                    contract.getMemo(),
                    contract.getNum(),
                    contract.getRealm(),
                    contract.getShard(),
                    migrationRan
                            ? contract.getType().toString()
                            : contract.getType().getId(),
                    String.format(
                            "(%d, %d)",
                            contract.getCreatedTimestamp(),
                            contract.getTimestampRange().lowerEndpoint()));
        }
    }

    private void persistContractsHistory(List<MigrationContractV1_47_1> contracts, boolean migrationRan) {
        for (MigrationContractV1_47_1 contract : contracts) {
            jdbcOperations.update(
                    getContractSql("contract_history", migrationRan),
                    contract.getId(),
                    contract.getCreatedTimestamp(),
                    contract.getMemo(),
                    contract.getNum(),
                    contract.getRealm(),
                    contract.getShard(),
                    migrationRan
                            ? contract.getType().toString()
                            : contract.getType().getId(),
                    String.format(
                            "(%d, %d)",
                            contract.getCreatedTimestamp(),
                            contract.getTimestampRange().lowerEndpoint()));
        }
    }

    private String getContractSql(String table, boolean useEnum) {
        return String.format(
                "insert into %s (id, created_timestamp, memo, num, realm, shard, type, " + "timestamp_range)"
                        + " values (?,?,?,?,?,?,%s,?::int8range)",
                table, useEnum ? "cast(? as entity_type)" : "?");
    }

    private String getEntitySql(String table, boolean useEnum) {
        return String.format(
                "insert into %s (id, created_timestamp, num, realm, shard, type, "
                        + "timestamp_range) values (?,?,?,?,?,%s,?::int8range)",
                table, useEnum ? "cast(? as entity_type)" : "?");
    }

    private List<MigrationEntityV1_47_1> findAllEntities() {
        return jdbcOperations.query(
                "select id, type from entity order by id", new BeanPropertyRowMapper<>(MigrationEntityV1_47_1.class));
    }

    private List<MigrationEntityV1_47_1> findAllEntitiesHistory() {
        return jdbcOperations.query(
                "select id, type from entity_history order by id",
                new BeanPropertyRowMapper<>(MigrationEntityV1_47_1.class));
    }

    private List<MigrationContractV1_47_1> findAllContracts() {
        return jdbcOperations.query(
                "select id, type from contract order by id",
                new BeanPropertyRowMapper<>(MigrationContractV1_47_1.class));
    }

    private List<MigrationContractV1_47_1> findAllContractsHistory() {
        return jdbcOperations.query(
                "select id, type from contract_history order by id",
                new BeanPropertyRowMapper<>(MigrationContractV1_47_1.class));
    }

    @Data
    @NoArgsConstructor
    private static class MigrationEntityV1_47_1 {
        private final long createdTimestamp = 1L;
        private long id;
        private long num;
        private final long realm = 0;
        private final long shard = 0;

        @JsonSerialize(using = RangeToStringSerializer.class)
        private final Range<Long> timestampRange = Range.atLeast(2L);

        private EntityType type;
    }

    @Data
    @NoArgsConstructor
    private static class MigrationContractV1_47_1 {
        private long createdTimestamp = 1L;
        private long id;
        private String memo = "Migration test";
        private long num;
        private long realm = 0;
        private long shard = 0;

        @JsonSerialize(using = RangeToStringSerializer.class)
        private Range<Long> timestampRange = Range.atLeast(2L);

        private EntityType type;
    }
}
