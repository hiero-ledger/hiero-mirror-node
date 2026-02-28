// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Strings;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.jspecify.annotations.NonNull;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.lifecycle.TestcontainersStartup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionOperations;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class CommonTestConfiguration {

    public static final String POSTGRESQL = "postgresql";

    @Value("#{environment.matchesProfiles('v2')}")
    private boolean v2;

    @Bean
    DomainBuilder domainBuilder(
            CommonProperties commonProperties,
            EntityManager entityManager,
            TransactionOperations transactionOperations) {
        return new DomainBuilder(commonProperties, entityManager, transactionOperations);
    }

    @Bean
    @ConfigurationProperties("spring.flyway")
    @Primary
    FlywayProperties flywayProperties() {
        final var baseLocation = "filesystem:../importer/src/main/resources/db/migration/";
        var placeholders = ImmutableMap.<String, String>builder()
                .put("api-password", "mirror_api_pass")
                .put("api-user", "mirror_api")
                .put("balancePartitionTimeInterval", "'10 years'")
                .put("db-name", "mirror_node")
                .put("db-user", "mirror_importer")
                .put("hashShardCount", "6")
                .put("partitionStartDate", "'1970-01-01'")
                .put("partitionTimeInterval", "'10 years'")
                .put("schema", "public")
                .put("shardCount", "2")
                .put("tempSchema", "temporary")
                .put("topicRunningHashV2AddedTimestamp", "0")
                .put("transactionHashLookbackInterval", "'60 days'")
                .build();

        var flywayProperties = new FlywayProperties();

        flywayProperties.setBaselineOnMigrate(true);
        flywayProperties.setBaselineVersion("0");
        flywayProperties.setConnectRetries(10);
        flywayProperties.setIgnoreMigrationPatterns(List.of("*:missing", "*:ignored"));
        flywayProperties.setLocations(List.of(baseLocation + "v1", baseLocation + "common"));
        flywayProperties.setPlaceholders(placeholders);
        flywayProperties.setTarget("latest");

        if (v2) {
            flywayProperties.setBaselineVersion("1.999.999");
            flywayProperties.setLocations(List.of(baseLocation + "v2", baseLocation + "common"));
        }

        return flywayProperties;
    }

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean(POSTGRESQL)
    PostgreSQLContainer postgresql() {
        var imageName = v2 ? "gcr.io/mirrornode/citus:12.1.1" : "gcr.io/mirrornode/postgres:16-alpine";
        var dockerImageName = DockerImageName.parse(imageName).asCompatibleSubstituteFor("postgres");
        var logger = LoggerFactory.getLogger(PostgreSQLContainer.class);
        var excluded = "terminating connection due to unexpected postmaster exit";
        var logConsumer = new FilteringConsumer(
                new Slf4jLogConsumer(logger, true),
                o -> !Strings.CS.contains(o.getUtf8StringWithoutLineEnding(), excluded));
        return new PostgreSQLContainer(dockerImageName)
                .withClasspathResourceMapping("init.sql", "/docker-entrypoint-initdb.d/init.sql", BindMode.READ_ONLY)
                .withDatabaseName("mirror_node")
                .withLogConsumer(logConsumer)
                .withPassword("mirror_node_pass")
                .withUsername("mirror_node");
    }

    // Avoid using @ServiceConnection and use our own custom connection details so we can pass mirror_importer as user
    @Bean
    JdbcConnectionDetails jdbcConnectionDetails(
            DataSourceProperties dataSourceProperties, PostgreSQLContainer postgresql) {
        TestcontainersStartup.start(postgresql);
        return new JdbcConnectionDetails() {

            @Override
            public @NonNull String getDriverClassName() {
                return postgresql.getDriverClassName();
            }

            @Override
            public @NonNull String getJdbcUrl() {
                return postgresql.getJdbcUrl();
            }

            @Override
            public String getPassword() {
                var password = dataSourceProperties.getPassword();
                return password.contains("importer") ? password : postgresql.getPassword();
            }

            @Override
            public String getUsername() {
                var username = dataSourceProperties.getUsername();
                return username.contains("importer") ? username : postgresql.getUsername();
            }
        };
    }

    @RequiredArgsConstructor
    public static class FilteringConsumer implements Consumer<OutputFrame> {

        private final Consumer<OutputFrame> delegate;
        private final Predicate<OutputFrame> filter;

        @Override
        public void accept(OutputFrame outputFrame) {
            if (filter.test(outputFrame)) {
                delegate.accept(outputFrame);
            }
        }
    }
}
