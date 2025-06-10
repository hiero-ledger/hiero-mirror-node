// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Objects;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.AfterEach;

abstract class AbstractAsyncJavaMigrationTest<T extends AsyncJavaMigration<?>> extends ImporterIntegrationTest {

    private static final String RESET_CHECKSUM_SQL =
            "update flyway_schema_history set checksum = -1 where description = ?";

    private static final String SELECT_LAST_CHECKSUM_SQL =
            """
            select (
              select checksum from flyway_schema_history
              where description = ?
              order by installed_rank desc
              limit 1
            )
            """;

    protected abstract T getMigration();

    @AfterEach
    void resetChecksum() {
        jdbcOperations.update(RESET_CHECKSUM_SQL, getDescription());
    }

    protected void waitForCompletion() {
        await().atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofMillis(100))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(isMigrationCompleted()).isTrue());
    }

    private String getDescription() {
        return getMigration().getDescription();
    }

    private boolean isMigrationCompleted() {
        var actual = jdbcOperations.queryForObject(SELECT_LAST_CHECKSUM_SQL, Integer.class, getDescription());
        return Objects.equals(actual, getMigration().getSuccessChecksum());
    }
}
