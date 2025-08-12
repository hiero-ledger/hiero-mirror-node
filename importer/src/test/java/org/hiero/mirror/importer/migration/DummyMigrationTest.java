// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.ImporterProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

@RequiredArgsConstructor
@Tag("migration")
class DummyMigrationTest extends ImporterIntegrationTest {

    private final ImporterProperties importerProperties;

    @Test
    void checksum() {
        var dummyMigration = new DummyMigration(importerProperties);
        assertThat(dummyMigration.getChecksum()).isEqualTo(5);
    }

    @Test
    void verifyPermissions() {
        final var sql =
                """
                create table if not exists test (id bigint primary key);
                insert into test (id) values (1);
                drop table test
                """;
        assertThatThrownBy(() -> jdbcOperations.update(sql)).isInstanceOf(DataAccessException.class);
        ownerJdbcTemplate.update(sql); // Succeeds
    }

    static class DummyMigration extends RepeatableMigration {

        @Getter
        private boolean migrated = false;

        public DummyMigration(ImporterProperties importerProperties) {
            super(importerProperties.getMigration());
        }

        @Override
        protected void doMigrate() {
            migrated = true;
        }

        @Override
        public String getDescription() {
            return "Dummy migration";
        }
    }
}
