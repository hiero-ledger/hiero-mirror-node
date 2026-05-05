// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import org.hiero.mirror.common.config.CommonRuntimeHints;
import org.hiero.mirror.common.converter.EntityIdToLongConverter;
import org.hiero.mirror.common.converter.JsonbReadingConverters;
import org.hiero.mirror.common.converter.JsonbWritingConverters;
import org.hiero.mirror.common.converter.LongArrayJdbcConverters;
import org.hiero.mirror.common.converter.LongToEntityIdConverter;
import org.hiero.mirror.common.converter.PGobjectToRangeReadingConverter;
import org.hiero.mirror.common.converter.PostgresAirdropStateJdbcConverters;
import org.hiero.mirror.common.converter.PostgresEntityTypeJdbcConverters;
import org.hiero.mirror.common.converter.PostgresHookJdbcConverters;
import org.hiero.mirror.common.converter.RangeToPGobjectWritingConverter;
import org.hiero.mirror.common.converter.ShortArrayJdbcConverters;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.util.DatabaseWaiter;
import org.hiero.mirror.common.util.SpelHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan("org.hiero.mirror")
@EnableConfigurationProperties(CommonProperties.class)
@EnableJdbcRepositories("org.hiero.mirror.common.repository") // Replaces JPA Repository scanning
@ImportRuntimeHints(CommonRuntimeHints.class)
public final class CommonConfiguration extends AbstractJdbcConfiguration {

    @Bean
    SystemEntity systemEntity(CommonProperties commonProperties) {
        return new SystemEntity(commonProperties);
    }

    @Bean
    DatabaseWaiter dbWaiter(CommonProperties commonProperties) {
        return new DatabaseWaiter(commonProperties);
    }

    @Bean("spelHelper")
    SpelHelper spelHelper() {
        return new SpelHelper();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariConfig hikariConfig() {
        return new HikariConfig();
    }

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    @Lazy
    DataSource dataSource(
            DataSourceProperties dataSourceProperties,
            HikariConfig hikariConfig,
            DatabaseWaiter databaseWaiter,
            ObjectProvider<JdbcConnectionDetails> detailsProvider) {

        var jdbcUrl = dataSourceProperties.determineUrl();
        var username = dataSourceProperties.determineUsername();
        var password = dataSourceProperties.determinePassword();

        final var connectionDetails = detailsProvider.getIfAvailable();
        if (connectionDetails != null) {
            jdbcUrl = connectionDetails.getJdbcUrl();
            username = connectionDetails.getUsername();
            password = connectionDetails.getPassword();
        }

        databaseWaiter.waitForDatabase(jdbcUrl, username, password);

        final var config = new HikariConfig();
        hikariConfig.copyStateTo(config);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        return new HikariDataSource(config);
    }

    @Override
    protected List<?> userConverters() {
        return List.of(
                new EntityIdToLongConverter(),
                new LongToEntityIdConverter(),
                new LongArrayJdbcConverters.AssociatedRegisteredNodeIdsToLongArray(),
                new LongArrayJdbcConverters.SqlArrayToAssociatedRegisteredNodeIds(),
                new LongArrayJdbcConverters.SqlArrayToLongList(),
                new LongArrayJdbcConverters.SqlArrayToLongArray(),
                new ShortArrayJdbcConverters.RegisteredNodeTypesHolderToShortArray(),
                new ShortArrayJdbcConverters.SqlArrayToRegisteredNodeTypesHolder(),
                new PostgresAirdropStateJdbcConverters.PostgresAirdropStateToPGobject(),
                new PostgresAirdropStateJdbcConverters.PGobjectToPostgresAirdropState(),
                new PostgresEntityTypeJdbcConverters.EntityTypeToJdbcValue(),
                new PostgresEntityTypeJdbcConverters.PGobjectToEntityType(),
                new PostgresEntityTypeJdbcConverters.StringToEntityType(),
                new PostgresHookJdbcConverters.HookExtensionPointToJdbcValue(),
                new PostgresHookJdbcConverters.PGobjectToHookExtensionPoint(),
                new PostgresHookJdbcConverters.StringToHookExtensionPoint(),
                new PostgresHookJdbcConverters.HookTypeToJdbcValue(),
                new PostgresHookJdbcConverters.PGobjectToHookType(),
                new PostgresHookJdbcConverters.StringToHookType(),
                new RangeToPGobjectWritingConverter(),
                new PGobjectToRangeReadingConverter(),
                new JsonbWritingConverters.FixedFeesHolderToJsonb(),
                new JsonbWritingConverters.FractionalFeesHolderToJsonb(),
                new JsonbWritingConverters.RoyaltyFeesHolderToJsonb(),
                new JsonbReadingConverters.PgobjectToFixedFeesHolder(),
                new JsonbReadingConverters.StringToFixedFeesHolder(),
                new JsonbReadingConverters.PgobjectToFractionalFeesHolder(),
                new JsonbReadingConverters.StringToFractionalFeesHolder(),
                new JsonbReadingConverters.PgobjectToRoyaltyFeesHolder(),
                new JsonbReadingConverters.StringToRoyaltyFeesHolder(),
                new JsonbWritingConverters.RegisteredServiceEndpointList(),
                new JsonbWritingConverters.ServiceEndpointsHolderToJsonb(),
                new JsonbReadingConverters.PgobjectToRegisteredServiceEndpointList(),
                new JsonbReadingConverters.PgobjectToServiceEndpointsHolder(),
                new JsonbReadingConverters.StringToRegisteredServiceEndpointList(),
                new JsonbReadingConverters.SqlArrayToShortList(),
                new JsonbWritingConverters.ItemizedTransferList(),
                new JsonbWritingConverters.NftTransferList(),
                new JsonbWritingConverters.AuthorizationList(),
                new JsonbWritingConverters.LedgerNodeContributionList(),
                new JsonbWritingConverters.ServiceEndpointSingle());
    }
}
