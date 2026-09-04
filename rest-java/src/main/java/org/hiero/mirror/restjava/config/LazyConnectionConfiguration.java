// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * Wraps the pooled DataSource so a physical connection is borrowed only when the first statement executes (and returned
 * at commit), rather than eagerly at transaction start as JdbcTransactionManager would otherwise do. This restores the
 * lazy acquisition JPA/Hibernate provided before the Spring Data JDBC migration and keeps read-only requests from
 * holding a pooled connection for their whole @Transactional method. Scoped to rest-java, whose high-concurrency read
 * API is the workload that benefits; the write-heavy importer and web3 keep the default eager behavior.
 */
@Configuration(proxyBeanMethods = false)
class LazyConnectionConfiguration {

    private static final String DATA_SOURCE_BEAN_NAME = "dataSource";

    @Bean
    static BeanPostProcessor lazyConnectionDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && DATA_SOURCE_BEAN_NAME.equals(beanName)) {
                    var lazyDataSource = new LazyConnectionDataSourceProxy(dataSource);
                    // Seed the default from the pool so the proxy doesn't open a probe connection on first use.
                    lazyDataSource.setDefaultAutoCommit(isAutoCommit(dataSource));
                    return lazyDataSource;
                }

                return bean;
            }
        };
    }

    private static boolean isAutoCommit(DataSource dataSource) {
        try {
            return dataSource.unwrap(HikariDataSource.class).isAutoCommit();
        } catch (SQLException e) {
            return true; // HikariCP's default
        }
    }
}
