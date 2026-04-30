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
import org.hiero.mirror.common.converter.LongToEntityIdConverter;
import org.hiero.mirror.common.converter.PGobjectToRangeReadingConverter;
import org.hiero.mirror.common.converter.RangeToPGobjectWritingConverter;
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
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.Table;

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

    /**
     * Mirror Node Naming Strategy.
     * Ensures class 'AccountBalance' maps to 'account_balance' and
     * respects the @Table annotation values.
     */
    @Bean
    public NamingStrategy namingStrategy() {
        return new NamingStrategy() {
            @Override
            public String getTableName(Class<?> type) {
                var table = type.getAnnotation(Table.class);
                if (table != null && !table.value().isEmpty()) {
                    return table.value();
                }
                return NamingStrategy.super.getTableName(type);
            }
        };
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
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(
                new EntityIdToLongConverter(),
                new LongToEntityIdConverter(),
                new RangeToPGobjectWritingConverter(), // Replaces @TypeRegistration
                new PGobjectToRangeReadingConverter(), // Replaces @TypeRegistration
                new JsonbWritingConverters.FixedFeeList(),
                new JsonbWritingConverters.FractionalFeeList(),
                new JsonbWritingConverters.RoyaltyFeeList(),
                new JsonbWritingConverters.RegisteredServiceEndpointList(),
                new JsonbReadingConverters.PgobjectToRegisteredServiceEndpointList(),
                new JsonbReadingConverters.StringToRegisteredServiceEndpointList(),
                new JsonbReadingConverters.SqlArrayToShortList(),
                new JsonbWritingConverters.ItemizedTransferList(),
                new JsonbWritingConverters.NftTransferList(),
                new JsonbWritingConverters.AuthorizationList(),
                new JsonbWritingConverters.LedgerNodeContributionList(),
                new JsonbWritingConverters.ServiceEndpointSingle()));
    }
}
