// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.util;

import com.google.common.util.concurrent.Uninterruptibles;
import java.sql.DriverManager;
import java.util.Properties;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.springframework.util.StringUtils;

@CustomLog
@RequiredArgsConstructor
public class DatabaseWaiter {
    private final CommonProperties commonProperties;

    public void waitForDatabase(String jdbcUrl, String username, String password) {
        final var properties = commonProperties.getDatabaseStartup();
        if (!properties.isEnabled()) {
            log.info("Database startup wait is disabled");
            return;
        }

        final var deadline = System.nanoTime() + properties.getTimeout().toNanos();
        final var jdbcProperties = new Properties();

        if (StringUtils.hasText(username)) {
            jdbcProperties.setProperty("user", username);
        }

        if (password != null) {
            jdbcProperties.setProperty("password", password);
        }

        jdbcProperties.setProperty(
                "connectTimeout", String.valueOf(properties.getConnectTimeout().toSeconds()));
        jdbcProperties.setProperty(
                "socketTimeout", String.valueOf(properties.getSocketTimeout().toSeconds()));

        Exception lastException = null;
        while (System.nanoTime() < deadline) {
            try (final var connection = DriverManager.getConnection(jdbcUrl, jdbcProperties)) {
                if (connection.isValid(
                        Math.toIntExact(properties.getValidationTimeout().toSeconds()))) {
                    log.info("Successfully connected to database");
                    return;
                }

                log.info("Database connection not valid yet. Retrying in {}", properties.getInterval());
            } catch (Exception e) {
                lastException = e;
                log.info(
                        "Database connection not ready yet. Retrying in {}: {}",
                        properties.getInterval(),
                        e.getMessage());
            }

            Uninterruptibles.sleepUninterruptibly(properties.getInterval());
        }

        throw new IllegalStateException(
                "Database did not become available within " + properties.getTimeout(), lastException);
    }
}
