// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import org.hiero.mirror.common.config.CommonRuntimeHints;
import org.hiero.mirror.common.config.NullIdQueryMappingConfiguration;
import org.hiero.mirror.common.converter.ByteArrayArrayJdbcConverters;
import org.hiero.mirror.common.converter.DigestAlgorithmJdbcConverters;
import org.hiero.mirror.common.converter.EntityIdToLongConverter;
import org.hiero.mirror.common.converter.JsonbReadingConverters;
import org.hiero.mirror.common.converter.JsonbWritingConverters;
import org.hiero.mirror.common.converter.LongArrayJdbcConverters;
import org.hiero.mirror.common.converter.LongToEntityIdConverter;
import org.hiero.mirror.common.converter.PGobjectToRangeReadingConverter;
import org.hiero.mirror.common.converter.PostgresAirdropStateJdbcConverters;
import org.hiero.mirror.common.converter.PostgresEntityTypeJdbcConverters;
import org.hiero.mirror.common.converter.PostgresErrataTypeJdbcConverters;
import org.hiero.mirror.common.converter.PostgresHookJdbcConverters;
import org.hiero.mirror.common.converter.PostgresTokenJdbcConverters;
import org.hiero.mirror.common.converter.RangeToPGobjectWritingConverter;
import org.hiero.mirror.common.converter.ReconciliationStatusJdbcConverters;
import org.hiero.mirror.common.converter.ShortArrayJdbcConverters;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.repository.AssignedIdDataAccessStrategy;
import org.hiero.mirror.common.repository.MergingJdbcRepository;
import org.hiero.mirror.common.util.DatabaseWaiter;
import org.hiero.mirror.common.util.SpelHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.core.convert.IdGeneratingEntityCallback;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.QueryMappingConfiguration;
import org.springframework.data.jdbc.core.convert.RelationResolver;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.DefaultQueryMappingConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jdbc.repository.config.JdbcConfiguration;
import org.springframework.data.mapping.callback.EntityCallbacks;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan("org.hiero.mirror")
@EnableConfigurationProperties(CommonProperties.class)
// Replaces JPA Repository scanning; the base class replicates JPA merge semantics for save()
@EnableJdbcRepositories(
        basePackages = "org.hiero.mirror.common.repository",
        repositoryBaseClass = MergingJdbcRepository.class)
@ImportRuntimeHints(CommonRuntimeHints.class)
public final class CommonConfiguration extends AbstractJdbcConfiguration {

    private final ObjectProvider<DataSource> dataSourceProvider;

    public CommonConfiguration(ObjectProvider<DataSource> dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

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

    // Pinned so the dialect never has to be resolved from a live connection through the NamedParameterJdbcTemplate
    // bean, which Boot orders after flywayInitializer - repositories would otherwise be unusable from flyway Java
    // migrations. Only PostgreSQL is supported.
    @Bean
    @Override
    public JdbcDialect jdbcDialect(@Lazy NamedParameterJdbcOperations operations) {
        return JdbcPostgresDialect.INSTANCE;
    }

    // Like jdbcDialect above, the converter and data access strategy are built on the DataSource directly instead of
    // the flyway-ordered NamedParameterJdbcTemplate bean (kept @Lazy and unused). Flyway Java migrations such as
    // ErrataMigration resolve repository-backed beans while migrating, so any dependency from the repository chain
    // (repositories -> jdbcAggregateTemplate -> jdbcConverter/dataAccessStrategy) on that bean is a circular
    // dependency that prevents the context from starting.
    @Bean
    @Override
    public JdbcConverter jdbcConverter(
            JdbcMappingContext mappingContext,
            @Lazy NamedParameterJdbcOperations operations,
            @Lazy RelationResolver relationResolver,
            JdbcCustomConversions conversions,
            JdbcDialect dialect) {
        return JdbcConfiguration.createConverter(
                mappingContext, internalJdbcOperations(), relationResolver, conversions, dialect);
    }

    @Bean
    @Override
    public IdGeneratingEntityCallback idGeneratingBeforeSaveCallback(
            JdbcMappingContext mappingContext, @Lazy NamedParameterJdbcOperations operations, JdbcDialect dialect) {
        return new IdGeneratingEntityCallback(mappingContext, dialect, internalJdbcOperations());
    }

    // Wrapped so inserts always provide the assigned id, even a primitive zero that Spring Data JDBC would otherwise
    // treat as database-generated and omit from the insert statement.
    @Bean
    @Override
    public DataAccessStrategy dataAccessStrategyBean(
            @Lazy NamedParameterJdbcOperations operations,
            JdbcConverter jdbcConverter,
            JdbcMappingContext context,
            JdbcDialect dialect) {
        return new AssignedIdDataAccessStrategy(
                JdbcConfiguration.createDataAccessStrategy(
                        internalJdbcOperations(), jdbcConverter, QueryMappingConfiguration.EMPTY, dialect),
                context,
                jdbcConverter,
                internalJdbcOperations(),
                dialect);
    }

    private NamedParameterJdbcOperations internalJdbcOperations() {
        return new NamedParameterJdbcTemplate(dataSourceProvider.getObject());
    }

    // Maps rows whose id column(s) are NULL to no entity, matching the previous JPA behavior for aggregate queries.
    // Module-specific row mappers can be contributed via a DefaultQueryMappingConfiguration bean.
    @Bean
    @Primary
    QueryMappingConfiguration queryMappingConfiguration(
            ApplicationContext applicationContext,
            JdbcMappingContext jdbcMappingContext,
            JdbcConverter jdbcConverter,
            ObjectProvider<DefaultQueryMappingConfiguration> customMappings) {
        var delegate = customMappings.getIfAvailable(() -> null);
        return new NullIdQueryMappingConfiguration(
                jdbcMappingContext,
                jdbcConverter,
                delegate != null ? delegate : QueryMappingConfiguration.EMPTY,
                EntityCallbacks.create(applicationContext));
    }

    @Override
    protected List<?> userConverters() {
        return List.of(
                new ByteArrayArrayJdbcConverters.MaxCustomFeesHolderToJdbcValue(),
                new ByteArrayArrayJdbcConverters.SqlArrayToMaxCustomFeesHolder(),
                new ByteArrayArrayJdbcConverters.PGobjectToByteArrayArray(),
                new EntityIdToLongConverter(),
                new LongToEntityIdConverter(),
                new DigestAlgorithmJdbcConverters.DigestAlgorithmToJdbcValue(),
                new DigestAlgorithmJdbcConverters.IntegerToDigestAlgorithm(),
                new ReconciliationStatusJdbcConverters.ReconciliationStatusToJdbcValue(),
                new ReconciliationStatusJdbcConverters.IntegerToReconciliationStatus(),
                new LongArrayJdbcConverters.AssociatedRegisteredNodeIdsToLongArray(),
                new LongArrayJdbcConverters.SqlArrayToAssociatedRegisteredNodeIds(),
                new LongArrayJdbcConverters.LongArrayToAssociatedRegisteredNodeIds(),
                new LongArrayJdbcConverters.SqlArrayToLongList(),
                new LongArrayJdbcConverters.SqlArrayToLongArray(),
                new ShortArrayJdbcConverters.RegisteredNodeTypesHolderToShortArray(),
                new ShortArrayJdbcConverters.SqlArrayToRegisteredNodeTypesHolder(),
                new ShortArrayJdbcConverters.ShortArrayToRegisteredNodeTypesHolder(),
                new PostgresAirdropStateJdbcConverters.PostgresAirdropStateToPGobject(),
                new PostgresAirdropStateJdbcConverters.PGobjectToPostgresAirdropState(),
                new PostgresAirdropStateJdbcConverters.StringToPostgresAirdropState(),
                new PostgresEntityTypeJdbcConverters.EntityTypeToJdbcValue(),
                new PostgresEntityTypeJdbcConverters.PGobjectToEntityType(),
                new PostgresEntityTypeJdbcConverters.StringToEntityType(),
                new PostgresErrataTypeJdbcConverters.ErrataTypeToPGobject(),
                new PostgresErrataTypeJdbcConverters.PGobjectToErrataType(),
                new PostgresErrataTypeJdbcConverters.StringToErrataType(),
                new PostgresHookJdbcConverters.HookExtensionPointToJdbcValue(),
                new PostgresHookJdbcConverters.PGobjectToHookExtensionPoint(),
                new PostgresHookJdbcConverters.StringToHookExtensionPoint(),
                new PostgresHookJdbcConverters.HookTypeToJdbcValue(),
                new PostgresHookJdbcConverters.PGobjectToHookType(),
                new PostgresHookJdbcConverters.StringToHookType(),
                new PostgresTokenJdbcConverters.TokenFreezeStatusEnumToJdbcValue(),
                new PostgresTokenJdbcConverters.TokenFreezeStatusEnumToInteger(),
                new PostgresTokenJdbcConverters.IntegerToTokenFreezeStatusEnum(),
                new PostgresTokenJdbcConverters.TokenKycStatusEnumToJdbcValue(),
                new PostgresTokenJdbcConverters.TokenKycStatusEnumToInteger(),
                new PostgresTokenJdbcConverters.IntegerToTokenKycStatusEnum(),
                new PostgresTokenJdbcConverters.TokenPauseStatusEnumToJdbcValue(),
                new PostgresTokenJdbcConverters.PGobjectToTokenPauseStatusEnum(),
                new PostgresTokenJdbcConverters.StringToTokenPauseStatusEnum(),
                new PostgresTokenJdbcConverters.TokenSupplyTypeEnumToJdbcValue(),
                new PostgresTokenJdbcConverters.PGobjectToTokenSupplyTypeEnum(),
                new PostgresTokenJdbcConverters.StringToTokenSupplyTypeEnum(),
                new PostgresTokenJdbcConverters.TokenTypeEnumToJdbcValue(),
                new PostgresTokenJdbcConverters.PGobjectToTokenTypeEnum(),
                new PostgresTokenJdbcConverters.StringToTokenTypeEnum(),
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
                new JsonbWritingConverters.ServiceEndpointsHolderToJsonb(),
                new JsonbReadingConverters.PgobjectToRegisteredServiceEndpointList(),
                new JsonbReadingConverters.PgobjectToServiceEndpointsHolder(),
                new JsonbReadingConverters.StringToRegisteredServiceEndpointList(),
                new JsonbReadingConverters.SqlArrayToShortList(),
                new JsonbWritingConverters.AccessListHolderToJsonb(),
                new JsonbWritingConverters.AuthorizationListHolderToJsonb(),
                new JsonbWritingConverters.ItemizedTransferListHolderToJsonb(),
                new JsonbWritingConverters.NftTransferListHolderToJsonb(),
                new JsonbWritingConverters.LedgerNodeContributionListHolderToJsonb(),
                new JsonbReadingConverters.PgobjectToAccessListHolder(),
                new JsonbReadingConverters.StringToAccessListHolder(),
                new JsonbReadingConverters.PgobjectToAuthorizationListHolder(),
                new JsonbReadingConverters.StringToAuthorizationListHolder(),
                new JsonbReadingConverters.PgobjectToItemizedTransferListHolder(),
                new JsonbReadingConverters.StringToItemizedTransferListHolder(),
                new JsonbReadingConverters.PgobjectToNftTransferListHolder(),
                new JsonbReadingConverters.StringToNftTransferListHolder(),
                new JsonbReadingConverters.PgobjectToLedgerNodeContributionListHolder(),
                new JsonbReadingConverters.StringToLedgerNodeContributionListHolder(),
                new JsonbWritingConverters.ServiceEndpointSingle(),
                new JsonbReadingConverters.PgobjectToServiceEndpoint(),
                new JsonbReadingConverters.StringToServiceEndpoint());
    }
}
