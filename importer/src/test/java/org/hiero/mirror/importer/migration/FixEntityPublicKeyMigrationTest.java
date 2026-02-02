// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = FixEntityPublicKeyMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
class FixEntityPublicKeyMigrationTest extends ImporterIntegrationTest {

    private static final RowMapper<MigrationEntity> rowMapper = rowMapper(MigrationEntity.class);

    @Test
    void empty() {
        runMigration();
        assertThat(findAllEntities()).isEmpty();
        assertThat(findAllHistory()).isEmpty();
    }

    @Test
    void noChange() {
        // given
        var entity1 = toMigrationEntity(domainBuilder.entity().get());
        // entity created with 1/2 threshold key so in database public key is already null
        var entity2 = toMigrationEntity(domainBuilder
                .entity()
                .customize(e -> e.key(domainBuilder.thresholdKey(2, 1)).publicKey(null))
                .get());

        var entityHistory1 =
                toMigrationEntityHistory(domainBuilder.entityHistory().get());
        var entityHistory2 = toMigrationEntityHistory(domainBuilder
                .entityHistory()
                .customize(e -> e.key(domainBuilder.thresholdKey(2, 1)).publicKey(null))
                .get());

        persistEntity(entity1);
        persistEntity(entity2);
        persistHistory(entityHistory1);
        persistHistory(entityHistory2);

        // when
        runMigration();

        // then
        assertThat(findAllEntities()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findAllHistory()).containsExactlyInAnyOrder(entityHistory1, entityHistory2);
    }

    @Test
    void shouldClearOldKey() {
        // given
        var entity = toMigrationEntity(domainBuilder
                .entity()
                .customize(e -> e.key(domainBuilder.thresholdKey(2, 1))
                        // must call after key() to override the side effect
                        .publicKey(domainBuilder.text(12))
                        .type(EntityType.CONTRACT))
                .get());

        var entityHistory = toMigrationEntityHistory(domainBuilder
                .entityHistory()
                .customize(e -> e.key(domainBuilder.thresholdKey(2, 1))
                        // must call after key() to override the side effect
                        .publicKey(domainBuilder.text(12))
                        .type(EntityType.CONTRACT))
                .get());

        persistEntity(entity);
        persistHistory(entityHistory);

        // when
        runMigration();

        // then
        entity.setPublicKey(null);
        entityHistory.setPublicKey(null);
        assertThat(findAllEntities()).containsExactly(entity);
        assertThat(findAllHistory()).containsExactly(entityHistory);
    }

    @Test
    void shouldHaveNonNullPublicKey() {
        // given entities should have a non-null public key however it's null in db - one with primitive key, the other
        // with a key list of one primitive key
        var entity1 = toMigrationEntity(
                domainBuilder.entity().customize(e -> e.publicKey(null)).get());
        var entity2 = toMigrationEntity(domainBuilder
                .entity()
                .customize(e -> e.key(domainBuilder.keyList(1)).publicKey(null))
                .get());
        var entityHistory1 = toMigrationEntityHistory(
                domainBuilder.entityHistory().customize(e -> e.publicKey(null)).get());
        var entityHistory2 = toMigrationEntityHistory(domainBuilder
                .entityHistory()
                .customize(e -> e.key(domainBuilder.keyList(1)).publicKey(null))
                .get());
        persistEntity(entity1);
        persistEntity(entity2);
        persistHistory(entityHistory1);
        persistHistory(entityHistory2);

        // when
        runMigration();

        // then
        var entities = List.of(entity1, entity2, entityHistory1, entityHistory2);
        entities.forEach(e -> e.setPublicKey(DomainUtils.getPublicKey(e.getKey())));

        assertThat(findAllEntities()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findAllHistory()).containsExactlyInAnyOrder(entityHistory1, entityHistory2);
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath =
                isV1() ? "v1/V1.106.0__fix_entity_public_key.sql" : "v2/V2.11.0__fix_entity_public_key.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        jdbcOperations.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    private void persistEntity(MigrationEntity entity) {
        jdbcOperations.update(
                "INSERT INTO entity (id, num, realm, shard, created_timestamp, timestamp_range, type, key, public_key) VALUES (?, ?, ?, ?, ?, ?::int8range, ?::entity_type, ?, ?)",
                entity.getId(),
                entity.getNum(),
                entity.getRealm(),
                entity.getShard(),
                entity.getCreatedTimestamp(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()),
                entity.getType().name(),
                entity.getKey(),
                entity.getPublicKey());
    }

    private void persistHistory(MigrationEntity entity) {
        jdbcOperations.update(
                "INSERT INTO entity_history (id, num, realm, shard, created_timestamp, timestamp_range, type, key, public_key) VALUES (?, ?, ?, ?, ?, ?::int8range, ?::entity_type, ?, ?)",
                entity.getId(),
                entity.getNum(),
                entity.getRealm(),
                entity.getShard(),
                entity.getCreatedTimestamp(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()),
                entity.getType().name(),
                entity.getKey(),
                entity.getPublicKey());
    }

    private List<MigrationEntity> findAllEntities() {
        return jdbcOperations.query("SELECT * FROM entity", rowMapper);
    }

    private List<MigrationEntity> findAllHistory() {
        return jdbcOperations.query("SELECT * FROM entity_history", rowMapper);
    }

    private MigrationEntity toMigrationEntity(Entity e) {
        return MigrationEntity.builder()
                .id(e.getId())
                .num(e.getNum())
                .realm(e.getRealm())
                .shard(e.getShard())
                .createdTimestamp(e.getCreatedTimestamp())
                .timestampRange(e.getTimestampRange())
                .type(e.getType())
                .key(e.getKey())
                .publicKey(e.getPublicKey())
                .build();
    }

    private MigrationEntity toMigrationEntityHistory(EntityHistory e) {
        return MigrationEntity.builder()
                .id(e.getId())
                .num(e.getNum())
                .realm(e.getRealm())
                .shard(e.getShard())
                .createdTimestamp(e.getCreatedTimestamp())
                .timestampRange(e.getTimestampRange())
                .type(e.getType())
                .key(e.getKey())
                .publicKey(e.getPublicKey())
                .build();
    }

    @Data
    @Builder
    private static class MigrationEntity {
        private Long id;
        private Long num;
        private Long realm;
        private Long shard;
        private Long createdTimestamp;
        private Range<Long> timestampRange;
        private EntityType type;
        private byte[] key;
        private String publicKey;
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.10.1" : "1.105.1";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
