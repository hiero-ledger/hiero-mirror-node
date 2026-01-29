// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.transaction.Authorization;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.repository.EntityHistoryRepository;
import org.hiero.mirror.importer.repository.EntityRepository;
import org.hiero.mirror.importer.repository.EthereumTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = AddDelegationIndicatorMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
class AddDelegationIndicatorMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_ENTITY_DDL = "alter table entity drop column if exists delegation_indicator";
    private static final String REVERT_ENTITY_HISTORY_DDL =
            "alter table entity_history drop column if exists delegation_indicator";
    private static final String REVERT_ETHEREUM_TX_DDL =
            "alter table ethereum_transaction drop column if exists authorization_list";

    private final DBProperties dbProperties;
    private final EntityRepository entityRepository;
    private final EntityHistoryRepository entityHistoryRepository;
    private final EthereumTransactionRepository ethereumTransactionRepository;

    @AfterEach
    void teardown() {
        ownerJdbcTemplate.update(REVERT_ENTITY_DDL);
        ownerJdbcTemplate.update(REVERT_ENTITY_HISTORY_DDL);
        ownerJdbcTemplate.update(REVERT_ETHEREUM_TX_DDL);
    }

    @Test
    void empty() {
        runMigration();

        assertThat(entityRepository.findAll()).isEmpty();
        assertThat(entityHistoryRepository.findAll()).isEmpty();
        assertThat(ethereumTransactionRepository.findAll()).isEmpty();
    }

    @Test
    void canInsertDataWithNewColumns() throws JsonProcessingException {
        // given
        runMigration();

        // when
        final var delegationIndicator = new byte[] {
            (byte) 0xef,
            0x01,
            0x00,
            0x01,
            0x02,
            0x03,
            0x04,
            0x05,
            0x06,
            0x07,
            0x08,
            0x09,
            0x0a,
            0x0b,
            0x0c,
            0x0d,
            0x0e,
            0x0f,
            0x10,
            0x11,
            0x12,
            0x13,
            0x14,
            0x15,
            0x16,
            0x17,
            0x18
        };
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.delegationIndicator(delegationIndicator))
                .persist();

        final var entityHistory = domainBuilder
                .entityHistory()
                .customize(eh -> eh.delegationIndicator(delegationIndicator))
                .persist();

        final var authorization = new Authorization()
                .toBuilder()
                        .address("0x123")
                        .chainId("0x1")
                        .nonce(5L)
                        .r("0x222")
                        .s("0x333")
                        .yParity(1)
                        .build();
        final var ethereumTxn = domainBuilder
                .ethereumTransaction(true)
                .customize(txn -> txn.authorizationList(List.of(authorization)))
                .persist();

        // then
        final var actualDelegationIndicator = jdbcOperations.queryForObject(
                "select delegation_indicator from entity where id = ?", byte[].class, entity.getId());
        assertThat(actualDelegationIndicator).isEqualTo(delegationIndicator);

        final var actualDelegationIndicatorHistory = jdbcOperations.queryForObject(
                "select delegation_indicator from entity_history where id = ?", byte[].class, entityHistory.getId());
        assertThat(actualDelegationIndicatorHistory).isEqualTo(delegationIndicator);

        final var actualAuthList = jdbcOperations.queryForObject(
                "select authorization_list::text from ethereum_transaction where consensus_timestamp = ?",
                String.class,
                ethereumTxn.getConsensusTimestamp());
        assertThat(actualAuthList).isNotNull();

        final var mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        final var actualTree = mapper.readTree(actualAuthList);
        final var expectedJson = mapper.writeValueAsString(List.of(authorization));
        assertThat(actualTree).isEqualTo(mapper.readTree(expectedJson));
    }

    @SneakyThrows
    private void runMigration() {
        String migrationFilepath =
                isV1() ? "v1/V1.116.0__add_delegation_indicator.sql" : "v2/V2.21.0__add_delegation_indicator.sql";
        var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.20.0" : "1.115.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
