/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.config;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.repository.RecordFileRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.hedera.mirror.web3.repository",
        entityManagerFactoryRef = "primaryEntityManager",
        transactionManagerRef = "primaryTransactionManager",
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = RecordFileRepository.class))
@ConditionalOnProperty(prefix = "hedera.mirror.web3.db", name = "primaryHost")
public class PrimaryDatasourceConfiguration {
    @Bean
    public LocalContainerEntityManagerFactoryBean primaryEntityManager(
            EntityManagerFactoryBuilder factoryBuilder,
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            @Qualifier("persistenceManagedTypesPrimary") PersistenceManagedTypes persistenceManagedTypes) {

        return factoryBuilder
                .dataSource(primaryDataSource)
                .managedTypes(persistenceManagedTypes)
                .build();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource primaryDataSource(
            DataSourceProperties properties,
            @Value("${hedera.mirror.web3.db.primaryHost}") String primaryHost,
            @Value("${hedera.mirror.web3.db.host}") String defaultHost) {
        var url = properties
                .determineUrl()
                .replaceAll("jdbc:postgresql://" + defaultHost, "jdbc:postgresql://" + primaryHost);
        var dataSource = DataSourceBuilder.create(properties.getClassLoader())
                .type(HikariDataSource.class)
                .driverClassName(properties.determineDriverClassName())
                .url(url)
                .username(properties.determineUsername())
                .password(properties.determinePassword())
                .build();

        if (StringUtils.hasText(properties.getName())) {
            dataSource.setPoolName("primary-" + properties.getName());
        }

        return dataSource;
    }

    @Bean
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManager") LocalContainerEntityManagerFactoryBean entityManager) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManager.getObject());
        return transactionManager;
    }

    @Bean
    public PersistenceManagedTypes persistenceManagedTypesPrimary(
            BeanFactory beanFactory, ResourceLoader resourceLoader) {
        String[] packagesToScan = getPackagesToScan(beanFactory);
        return new PersistenceManagedTypesScanner(
                        resourceLoader, value -> RecordFile.class.getName().equals(value))
                .scan(packagesToScan);
    }

    private String[] getPackagesToScan(BeanFactory beanFactory) {
        List<String> packages = EntityScanPackages.get(beanFactory).getPackageNames();
        if (packages.isEmpty() && AutoConfigurationPackages.has(beanFactory)) {
            packages = AutoConfigurationPackages.get(beanFactory);
        }
        return StringUtils.toStringArray(packages);
    }
}
