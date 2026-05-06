// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import static org.hibernate.cfg.JdbcSettings.STATEMENT_INSPECTOR;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hiero.mirror.importer.db.DBProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class HibernateConfiguration implements HibernatePropertiesCustomizer {
    private final DBProperties dbProperties;

    private static final String NO_LOAD_BALANCE = "/* NO PGPOOL LOAD BALANCE */\n";

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        if (!dbProperties.isLoadBalance()) {
            hibernateProperties.put(STATEMENT_INSPECTOR, statementInspector());
        }
    }

    /**
     * https://www.pgpool.net/docs/latest/en/html/runtime-config-load-balancing.html pgpool disables load balancing for
     * SQL statements beginning with an arbitrary comment and sends them to the primary node. This is used to prevent
     * the stale read-after-write issue.
     */
    private StatementInspector statementInspector() {
        return sql -> NO_LOAD_BALANCE + sql;
    }
}
