// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.converter.ObjectToStringSerializer;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.repository.LedgerHistoryRepository;
import org.hiero.mirror.importer.repository.LedgerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = LedgerHistoryMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class LedgerHistoryMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_DDL = """
            drop table if exists ledger_history;
            delete from ledger;
            alter table if exists ledger
                drop column if exists timestamp_range,
                add column if not exists consensus_timestamp bigint not null,
                add column if not exists history_proof_verification_key bytea not null;
            """;

    private final LedgerHistoryRepository ledgerHistoryRepository;
    private final LedgerRepository ledgerRepository;

    @AfterEach
    void teardown() {
        ownerJdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(ledgerRepository.findAll()).isEmpty();
        assertThat(ledgerHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        final var ledger1 = persistLedger();
        final var ledger2 = persistLedger();

        // when
        runMigration();

        // then
        assertThat(ledgerRepository.findAll()).containsExactlyInAnyOrder(ledger1, ledger2);
        assertThat(ledgerRepository.findLatest()).contains(ledger2);
        assertThat(ledgerHistoryRepository.findAll()).isEmpty();
    }

    @SneakyThrows
    private Ledger persistLedger() {
        final var ledger = domainBuilder.ledger().get();
        final var nodeContributions =
                ObjectToStringSerializer.OBJECT_MAPPER.writeValueAsString(ledger.getNodeContributions());
        jdbcOperations.update(
                """
                insert into ledger (consensus_timestamp, history_proof_verification_key, ledger_id, node_contributions)
                values (?, ?, ?, ?::jsonb)
                """, ledger.getTimestampLower(), domainBuilder.bytes(32), ledger.getLedgerId(), nodeContributions);
        return ledger;
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath =
                isV1() ? "v1/V1.129.1__drop_ledger_proof_columns.sql" : "v2/V2.34.1__drop_ledger_proof_columns.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.update(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(final ConfigurableApplicationContext configurableApplicationContext) {
            final var environment = configurableApplicationContext.getEnvironment();
            final var version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.34.0" : "1.129.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
