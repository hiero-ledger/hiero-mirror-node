// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import javax.sql.DataSource;
import org.hiero.mirror.common.config.StatementInterceptingDataSource;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.Web3Properties.ApiEndpointName;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.QueryTimeoutException;

/**
 * Enforces {@link Web3Properties#getRequestTimeout(ApiEndpointName)} on every SQL statement issued during a contract call. Replaces
 * the Hibernate StatementInspector used before the Spring Data JDBC migration: the DataSource is wrapped so each
 * statement preparation checks the elapsed request time and aborts with a {@link QueryTimeoutException} once the
 * deadline has passed. Statements issued outside a {@link ContractCallContext} are unaffected.
 */
@Configuration(proxyBeanMethods = false)
class QueryTimeoutConfiguration {

    // Static so the configuration class itself doesn't have to be instantiated before other BeanPostProcessors
    @Bean
    static BeanPostProcessor queryTimeoutDataSourcePostProcessor(ObjectProvider<Web3Properties> web3Properties) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof QueryTimeoutDataSource)) {
                    return new QueryTimeoutDataSource(dataSource, web3Properties);
                }

                return bean;
            }
        };
    }

    private static final class QueryTimeoutDataSource extends StatementInterceptingDataSource {

        private final ObjectProvider<Web3Properties> web3PropertiesProvider;
        private volatile @Nullable Web3Properties web3Properties;

        private QueryTimeoutDataSource(
                final DataSource targetDataSource, final ObjectProvider<Web3Properties> web3PropertiesProvider) {
            super(targetDataSource);
            this.web3PropertiesProvider = web3PropertiesProvider;
        }

        @Override
        protected void onStatement(Object[] args) {
            checkDeadline();
        }

        private void checkDeadline() {
            if (!ContractCallContext.isInitialized()) {
                return;
            }

            final var context = ContractCallContext.get();
            final long timeout =
                    getWeb3Properties().getRequestTimeout(context.getApi()).toMillis();
            final long elapsed = System.currentTimeMillis() - context.getStartTime();
            if (elapsed >= timeout) {
                throw new QueryTimeoutException("Transaction timed out after %s ms".formatted(elapsed));
            }
        }

        // Resolved lazily since the DataSource is post-processed before Web3Properties is available. Only the bean is
        // cached, not the timeout, as the timeout depends on the API endpoint of the current request
        private Web3Properties getWeb3Properties() {
            var properties = web3Properties;
            if (properties == null) {
                properties = web3PropertiesProvider.getObject();
                web3Properties = properties;
            }

            return properties;
        }
    }
}
