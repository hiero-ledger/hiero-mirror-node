// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = FixAsyncJavaMigrationHistoryMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@Tag("migration")
class FixAsyncJavaMigrationHistoryMigrationTest extends AsyncJavaMigrationBaseTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            '', ''
            '', '-1,-2'
            '', '-1,-2,1',
            '-1,-2,1', ''
            '-1,-2,1', '-1,-2,1'
            '-1,-2', ''
            '-1,-2', '-1,-2'
            '-1,-2', '-1,-2,1'
            """)
    void noRowsRemoved(String preChecksums, String postChecksums) {
        // given
        var rank = new AtomicInteger(1000);
        var expected = ListUtils.union(
                addMigrationHistories(preChecksums, rank, SCRIPT_HEDERA),
                addMigrationHistories(postChecksums, rank, SCRIPT));

        // when
        runMigration();

        // then
        assertThat(getAllMigrationHistory())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("executionTime")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void removeIncorrectRows() {
        // given
        var rank = new AtomicInteger(1000);
        var expected = addMigrationHistories("-1,-2,1", rank, SCRIPT_HEDERA);
        addMigrationHistories("-1,-2", rank, SCRIPT);

        // when
        runMigration();

        // then
        assertThat(getAllMigrationHistory())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("executionTime")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath = isV1()
                ? "v1/V1.109.0__fix_async_java_migration_history.sql"
                : "v2/V2.14.0__fix_async_java_migration_history.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    private List<MigrationHistory> addMigrationHistories(String checksums, AtomicInteger rank, String script) {
        if (StringUtils.isEmpty(checksums)) {
            return Collections.emptyList();
        }

        var histories = new ArrayList<MigrationHistory>();
        for (var value : checksums.split(",")) {
            int checksum = Integer.parseInt(value);
            var migrationHistory = new MigrationHistory(checksum, ELAPSED, rank.getAndIncrement(), script);
            addMigrationHistory(migrationHistory);
            histories.add(migrationHistory);
        }

        return histories;
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.13.0" : "1.108.0";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
