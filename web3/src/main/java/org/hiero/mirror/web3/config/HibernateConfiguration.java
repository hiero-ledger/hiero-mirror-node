// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.config;

import static org.hibernate.cfg.JdbcSettings.STATEMENT_INSPECTOR;

import java.util.Map;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.QueryTimeoutException;

@CustomLog
@Configuration
@RequiredArgsConstructor
class HibernateConfiguration implements HibernatePropertiesCustomizer {

    private final Web3Properties web3Properties;

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(STATEMENT_INSPECTOR, statementInspector());
    }

    @Bean
    StatementInspector statementInspector() {
        long timeout = web3Properties.getRequestTimeout().toMillis();
        return sql -> {
            if (!ContractCallContext.isInitialized()) {
                return sql;
            }

            var startTime = ContractCallContext.get().getStartTime();
            long elapsed = System.currentTimeMillis() - startTime;

            if (elapsed >= timeout) {
                throw new QueryTimeoutException("Transaction timed out after %s ms".formatted(elapsed));
            }

            return sql;
        };
    }
}
