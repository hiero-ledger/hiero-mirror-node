// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = AsyncJavaMigrationPreFixTest.Initializer.class)
@Tag("migration")
class AsyncJavaMigrationPreFixTest extends AsyncJavaMigrationBaseTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            , , -1
            , -1, -2
            , 1, 1
            1, , 1
            1, -1, -1
            1, 1, 1
            -1, , -2
            -1, -1, -2
            -1, 1, 1
            """)
    void getChecksum(Integer preRenamingChecksum, Integer postRenamingChecksum, int expected) {
        // given
        addMigrationHistory(new MigrationHistory(preRenamingChecksum, ELAPSED, 1000, SCRIPT_HEDERA));
        addMigrationHistory(new MigrationHistory(postRenamingChecksum, ELAPSED, 1100, SCRIPT));
        var migration = new TestAsyncJavaMigration(false, new MigrationProperties(), 1);

        // when, then
        assertThat(migration.getChecksum()).isEqualTo(expected);
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
