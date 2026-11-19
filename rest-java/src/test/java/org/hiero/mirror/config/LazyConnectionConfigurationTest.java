// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.SneakyThrows;
import org.hiero.mirror.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

class LazyConnectionConfigurationTest extends RestJavaIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionOperations transactionOperations;

    @Test
    void dataSourceIsLazy() {
        assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
    }

    @Test
    @SneakyThrows
    void connectionAcquiredLazilyWithinTransaction() {
        var pool = hikariPool();
        var jdbcTemplate = new JdbcTemplate(dataSource);
        int baseline = pool.getActiveConnections();

        transactionOperations.executeWithoutResult(status -> {
            // Entering the transaction must not borrow a physical connection from the pool...
            assertThat(pool.getActiveConnections())
                    .as("no connection acquired at transaction start")
                    .isEqualTo(baseline);

            // ...it is only materialized when the first statement runs.
            jdbcTemplate.queryForObject("select 1", Integer.class);
            assertThat(pool.getActiveConnections())
                    .as("connection acquired on first statement")
                    .isEqualTo(baseline + 1);
        });

        // And it is returned to the pool once the transaction completes.
        assertThat(pool.getActiveConnections())
                .as("connection released after commit")
                .isEqualTo(baseline);
    }

    @Test
    @SneakyThrows
    void rawPoolAcquiresEagerly() {
        // Contrast: without the lazy proxy, a DataSource-backed transaction manager borrows a connection at transaction
        // start, which is the behavior that increased the probability of pool exhaustion after the JPA -> Spring Data
        // JDBC migration.
        var hikari = dataSource.unwrap(HikariDataSource.class);
        var pool = hikari.getHikariPoolMXBean();
        var eagerTransaction = new TransactionTemplate(new DataSourceTransactionManager(hikari));
        int baseline = pool.getActiveConnections();

        eagerTransaction.executeWithoutResult(status -> assertThat(pool.getActiveConnections())
                .as("raw pool acquires a connection before any statement")
                .isEqualTo(baseline + 1));
    }

    private HikariPoolMXBean hikariPool() throws SQLException {
        return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
    }
}
