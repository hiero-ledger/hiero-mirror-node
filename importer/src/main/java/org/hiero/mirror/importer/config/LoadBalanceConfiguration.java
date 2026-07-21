// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hiero.mirror.importer.db.DBProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * https://www.pgpool.net/docs/latest/en/html/runtime-config-load-balancing.html pgpool disables load balancing for
 * SQL statements beginning with an arbitrary comment and sends them to the primary node. This is used to prevent
 * the stale read-after-write issue. Replaces the Hibernate StatementInspector used before the Spring Data JDBC
 * migration: when load balancing is disabled, the primary DataSource is wrapped so every prepared statement's SQL is
 * prefixed with the comment. The flyway DataSource is left untouched, matching the previous behavior where only the
 * Hibernate-issued statements were prefixed.
 */
@Configuration(proxyBeanMethods = false)
class LoadBalanceConfiguration {

    private static final String DATA_SOURCE_BEAN_NAME = "dataSource";

    @Bean
    static BeanPostProcessor noLoadBalanceDataSourcePostProcessor(ObjectProvider<DBProperties> dbProperties) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && DATA_SOURCE_BEAN_NAME.equals(beanName)) {
                    return new NoLoadBalanceDataSource(dataSource, dbProperties);
                }

                return bean;
            }
        };
    }

    private static final class NoLoadBalanceDataSource extends DelegatingDataSource {

        private static final String NO_LOAD_BALANCE = "/* NO PGPOOL LOAD BALANCE */\n";

        private final ObjectProvider<DBProperties> dbProperties;
        private volatile Boolean loadBalance;

        private NoLoadBalanceDataSource(DataSource targetDataSource, ObjectProvider<DBProperties> dbProperties) {
            super(targetDataSource);
            this.dbProperties = dbProperties;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrap(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return wrap(super.getConnection(username, password));
        }

        private Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    NoLoadBalanceDataSource.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        var name = method.getName();
                        if ((name.equals("prepareStatement") || name.equals("prepareCall"))
                                && args != null
                                && args.length > 0
                                && args[0] instanceof String sql
                                && !isLoadBalance()) {
                            args[0] = NO_LOAD_BALANCE + sql;
                        }

                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    });
        }

        private boolean isLoadBalance() {
            var balance = loadBalance;
            if (balance == null) {
                balance = dbProperties.getObject().isLoadBalance();
                loadBalance = balance;
            }

            return balance;
        }
    }
}
