// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = FixEntityPublicKeyMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
class FixEntityPublicKeyMigrationTest extends ImporterIntegrationTest {

    private final EntityRepository entityRepository;

    @Test
    void empty() {
        runMigration();
        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(findHistory(EntityHistory.class)).isEmpty();
    }

    @Test
    void noChange() {
        // given
        var entity1 = domainBuilder.entity().persist();
        // entity created with 1/2 threshold key so in database public key is already null
        var entity2 = domainBuilder
                .entity()
                .customize(e -> e.key(domainBuilder.thresholdKey(2, 1)).publicKey(null))
                .persist();
        // history table
        var entityHistory1 = domainBuilder.entityHistory().persist();
        // history of an entity created with 1/2 threshold key so in database public key is already null
        var entityHistory2 = domainBuilder
                .entityHistory()
                .customize(e -> e.key(domainBuilder.thresholdKey(2, 1)).publicKey(null))
                .persist();

        // when
        runMigration();

        // then
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findHistory(EntityHistory.class)).containsExactlyInAnyOrder(entityHistory1, entityHistory2);
    }

    @Test
    void shouldClearOldKey() {
        // given old public key stays after key gets changed to 1/2 threshold key
        var entity = domainBuilder
                .entity()
                .customize(e -> e.key(domainBuilder.thresholdKey(2, 1))
                        // must call after key() to override the side effect
                        .publicKey(domainBuilder.text(12))
                        .type(EntityType.CONTRACT))
                .persist();
        var entityHistory = domainBuilder
                .entityHistory()
                .customize(e -> e.key(domainBuilder.thresholdKey(2, 1))
                        // must call after key() to override the side effect
                        .publicKey(domainBuilder.text(12))
                        .type(EntityType.CONTRACT))
                .persist();

        // when
        runMigration();

        // then
        entity.setPublicKey(null);
        entityHistory.setPublicKey(null);
        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(findHistory(EntityHistory.class)).containsExactly(entityHistory);
    }

    @Test
    void shouldHaveNonNullPublicKey() {
        // given entities should have a non-null public key however it's null in db - one with primitive key, the other
        // with a key list of one primitive key
        var entity1 = domainBuilder.entity().customize(e -> e.publicKey(null)).persist();
        var entity2 = domainBuilder
                .entity()
                .customize(e -> e.key(domainBuilder.keyList(1)).publicKey(null))
                .persist();
        var entityHistory1 =
                domainBuilder.entityHistory().customize(e -> e.publicKey(null)).persist();
        var entityHistory2 = domainBuilder
                .entityHistory()
                .customize(e -> e.key(domainBuilder.keyList(1)).publicKey(null))
                .persist();

        // when
        runMigration();

        // then
        var entities = List.of(entity1, entity2, entityHistory1, entityHistory2);
        entities.forEach(e -> e.setPublicKey(DomainUtils.getPublicKey(e.getKey())));
        // sanity check
        assertThat(entities).map(AbstractEntity::getPublicKey).doesNotContainNull();
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2);
        assertThat(findHistory(EntityHistory.class)).containsExactlyInAnyOrder(entityHistory1, entityHistory2);
    }

    @SneakyThrows
    private void runMigration() {
        String migrationFilepath =
                isV1() ? "v1/V1.106.0__fix_entity_public_key.sql" : "v2/V2.11.0__fix_entity_public_key.sql";
        var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        jdbcOperations.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
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
