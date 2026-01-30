// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.History;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.hiero.mirror.common.domain.topic.Topic;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = TopicCustomFeesMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
class TopicCustomFeesMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_DDL = """
            alter table if exists transaction drop column if exists max_custom_fees;
            alter table if exists custom_fee rename column entity_id to token_id;
            alter table if exists custom_fee_history rename column entity_id to token_id;
            alter table if exists entity add column if not exists submit_key bytea;
            alter table if exists entity_history add column if not exists submit_key bytea;
            drop table if exists topic_history;
            drop table if exists topic;
            """;

    private final List<CustomFee> expectedCustomFees = new ArrayList<>();
    private final List<Entity> expectedEntities = new ArrayList<>();
    private final List<Topic> expectedTopics = new ArrayList<>();
    private final List<Entity> expectedHistoricalEntities = new ArrayList<>();
    private final List<Topic> expectedHistoricalTopics = new ArrayList<>();

    @AfterEach
    void cleanup() {
        ownerJdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(count("custom_fee")).isZero();
        assertThat(count("custom_fee_history")).isZero();
        assertThat(count("entity")).isZero();
        assertThat(count("entity_history")).isZero();
        assertThat(count("topic")).isZero();
        assertThat(count("topic_history")).isZero();
    }

    @Test
    void migrate() {
        // given
        var entities = new ArrayList<PreMigrationEntity>();

        // A topic whose create transaction wasn't ingested
        entities.add(nullifyCreatedTimestamp(getEntity(EntityType.TOPIC)));

        entities.add(getEntity(EntityType.ACCOUNT));
        entities.add(updateEntity(entities.getLast()));

        entities.add(getEntity(EntityType.CONTRACT));
        entities.add(updateEntity(entities.getLast()));

        entities.add(emptyAdminAndSubmitKey(getEntity(EntityType.TOPIC)));
        entities.add(updateEntity(entities.getLast()));
        entities.add(updateEntity(entities.getLast()));

        entities.add(getEntity(EntityType.TOPIC));

        persistEntities(entities);

        // when
        runMigration();

        // then
        assertThat(findAllCustomFees()).containsExactlyInAnyOrderElementsOf(expectedCustomFees);
        assertThat(findAllCustomFeeHistory()).isEmpty();
        assertThat(findAllEntities())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("publicKey", "delegationIndicator")
                .containsExactlyInAnyOrderElementsOf(expectedEntities);
        assertThat(findAllEntityHistory())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("publicKey", "delegationIndicator")
                .containsExactlyInAnyOrderElementsOf(expectedHistoricalEntities);
        assertThat(findAllTopics()).containsExactlyInAnyOrderElementsOf(expectedTopics);
        assertThat(findAllTopicHistory()).containsExactlyInAnyOrderElementsOf(expectedHistoricalTopics);
    }

    private PreMigrationEntity emptyAdminAndSubmitKey(PreMigrationEntity entity) {
        if (entity.getType() != EntityType.TOPIC) {
            return entity;
        }

        // this replicates the incorrect importer logic for topic: admin key and submit key are set to empty byte array
        // when not set in the transaction body
        entity.setKey(ArrayUtils.EMPTY_BYTE_ARRAY);
        entity.setSubmitKey(ArrayUtils.EMPTY_BYTE_ARRAY);
        return entity;
    }

    private PreMigrationEntity nullifyCreatedTimestamp(PreMigrationEntity entity) {
        if (entity.getType() != EntityType.TOPIC) {
            return entity;
        }

        entity.setCreatedTimestamp(null);
        entity.setKey(null);
        entity.setTimestampLower(0L);
        return entity;
    }

    private PreMigrationEntity getEntity(EntityType type) {
        long timestamp = domainBuilder.timestamp();
        return PreMigrationEntity.builder()
                .createdTimestamp(timestamp)
                .id(domainBuilder.id())
                .key(domainBuilder.key())
                .submitKey(getSubmitKey(type))
                .timestampRange(Range.atLeast(timestamp))
                .type(type)
                .build();
    }

    private byte[] getSubmitKey(EntityType type) {
        if (type != EntityType.TOPIC) {
            return null;
        }

        return domainBuilder.id() % 2 == 0 ? domainBuilder.bytes(16) : null;
    }

    private void persistEntities(List<PreMigrationEntity> entities) {
        var currentEntities = new ArrayList<PreMigrationEntity>();
        var historicalEntities = new ArrayList<PreMigrationEntity>();

        for (var entity : entities) {
            var postMigrationEntity = Entity.builder()
                    .createdTimestamp(entity.getCreatedTimestamp())
                    .declineReward(false)
                    .deleted(false)
                    .ethereumNonce(0L)
                    .id(entity.getId())
                    .key(entity.getKey())
                    .memo("")
                    .num(entity.getNum())
                    .realm(entity.getRealm())
                    .shard(entity.getShard())
                    .timestampRange(entity.getTimestampRange())
                    .type(entity.getType())
                    .build();
            var topic = entity.getType() == EntityType.TOPIC
                    ? Topic.builder()
                            .adminKey(ArrayUtils.isEmpty(entity.getKey()) ? null : entity.getKey())
                            .createdTimestamp(entity.getCreatedTimestamp())
                            .id(entity.getId())
                            .submitKey(ArrayUtils.isEmpty(entity.getSubmitKey()) ? null : entity.getSubmitKey())
                            .timestampRange(entity.getTimestampRange())
                            .build()
                    : null;

            if (entity.getTimestampUpper() == null) {
                currentEntities.add(entity);
                expectedEntities.add(postMigrationEntity);

                if (topic != null) {
                    expectedTopics.add(topic);

                    long createdTimestamp = entity.getCreatedTimestamp() != null ? entity.getCreatedTimestamp() : 0;
                    expectedCustomFees.add(CustomFee.builder()
                            .fixedFees(Collections.emptyList())
                            .entityId(entity.getId())
                            .timestampRange(Range.atLeast(createdTimestamp))
                            .build());
                }
            } else {
                historicalEntities.add(entity);
                expectedHistoricalEntities.add(postMigrationEntity);

                if (topic != null) {
                    expectedHistoricalTopics.add(topic);
                }
            }
        }

        ParameterizedPreparedStatementSetter<PreMigrationEntity> pss = (ps, entity) -> {
            if (entity.getCreatedTimestamp() != null) {
                ps.setLong(1, entity.getCreatedTimestamp());
            } else {
                ps.setNull(1, Types.BIGINT);
            }

            ps.setLong(2, entity.getId());
            ps.setBytes(3, entity.getKey());
            ps.setLong(4, entity.getNum());
            ps.setLong(5, entity.getRealm());
            ps.setLong(6, entity.getShard());
            ps.setBytes(7, entity.getSubmitKey());
            ps.setString(8, PostgreSQLGuavaRangeType.INSTANCE.asString(entity.getTimestampRange()));
            ps.setString(9, entity.getType().toString());
        };

        jdbcOperations.batchUpdate("""
            insert into entity (created_timestamp, id, key, num, realm, shard, submit_key, timestamp_range, type)
            values (?, ?, ?, ?, ?, ?, ?, ?::int8range, ?::entity_type)
            """, currentEntities, currentEntities.size(), pss);

        if (!historicalEntities.isEmpty()) {
            jdbcOperations.batchUpdate("""
            insert into entity_history (created_timestamp, id, key, num, realm, shard, submit_key, timestamp_range, type)
            values (?, ?, ?, ?, ?, ?, ?, ?::int8range, ?::entity_type)
            """, historicalEntities, historicalEntities.size(), pss);
        }
    }

    private PreMigrationEntity updateEntity(PreMigrationEntity previous) {
        long timestamp = domainBuilder.timestamp();
        previous.setTimestampUpper(timestamp);
        return previous.toBuilder()
                .submitKey(getSubmitKey(previous.getType()))
                .timestampRange(Range.atLeast(timestamp))
                .build();
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath =
                isV1() ? "v1/V1.103.2__topic_custom_fees.sql" : "v2/V2.8.2__topic_custom_fees.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    private long count(String table) {
        return jdbcOperations.queryForObject("select count(*) from " + table, Long.class);
    }

    private List<Entity> findAllEntities() {
        return jdbcOperations.query("select * from entity", entityRowMapper);
    }

    private List<Entity> findAllEntityHistory() {
        return jdbcOperations.query("select * from entity_history", entityRowMapper);
    }

    private List<Topic> findAllTopics() {
        return jdbcOperations.query("select * from topic", topicRowMapper);
    }

    private List<Topic> findAllTopicHistory() {
        return jdbcOperations.query("select * from topic_history", topicRowMapper);
    }

    private List<CustomFee> findAllCustomFees() {
        return jdbcOperations.query("select * from custom_fee", customFeeRowMapper);
    }

    private List<CustomFee> findAllCustomFeeHistory() {
        return jdbcOperations.query("select * from custom_fee_history", customFeeRowMapper);
    }

    private final RowMapper<Entity> entityRowMapper = (rs, rowNum) -> Entity.builder()
            .id(rs.getLong("id"))
            .num(rs.getLong("num"))
            .realm(rs.getLong("realm"))
            .shard(rs.getLong("shard"))
            .createdTimestamp((Long) rs.getObject("created_timestamp"))
            .timestampRange(PostgreSQLGuavaRangeType.longRange(rs.getString("timestamp_range")))
            .type(EntityType.valueOf(rs.getString("type")))
            .key(rs.getBytes("key"))
            .memo(rs.getString("memo"))
            .deleted(rs.getBoolean("deleted"))
            .declineReward(rs.getBoolean("decline_reward"))
            .ethereumNonce((Long) rs.getObject("ethereum_nonce"))
            .build();

    private final RowMapper<Topic> topicRowMapper = (rs, rowNum) -> Topic.builder()
            .id(rs.getLong("id"))
            .createdTimestamp((Long) rs.getObject("created_timestamp"))
            .timestampRange(PostgreSQLGuavaRangeType.longRange(rs.getString("timestamp_range")))
            .adminKey(rs.getBytes("admin_key"))
            .submitKey(rs.getBytes("submit_key"))
            .build();

    private final RowMapper<CustomFee> customFeeRowMapper = (rs, rowNum) -> CustomFee.builder()
            .entityId(rs.getLong("entity_id"))
            .fixedFees(Collections.emptyList())
            .timestampRange(PostgreSQLGuavaRangeType.longRange(rs.getString("timestamp_range")))
            .build();

    @Builder(toBuilder = true)
    @Data
    static class PreMigrationEntity implements History {
        private Long createdTimestamp;
        private long id;
        private byte[] key;
        private long num;
        private long realm;
        private long shard;
        private byte[] submitKey;
        private Range<Long> timestampRange;
        private EntityType type;
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.8.1" : "1.103.1";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
