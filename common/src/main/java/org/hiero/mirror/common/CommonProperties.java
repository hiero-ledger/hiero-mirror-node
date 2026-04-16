// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.common")
public class CommonProperties {

    private static final AtomicReference<CommonProperties> INSTANCE = new AtomicReference<>();

    @Valid
    @NotNull
    private DatabaseStartupProperties databaseStartup = new DatabaseStartupProperties();

    @Min(0)
    private long realm = 0L;

    @Min(0)
    private long shard = 0L;

    @PostConstruct
    public void init() {
        INSTANCE.set(this);
    }

    /**
     * This method returns the singleton instance of CommonProperties.
     * It is unsafe to call this method before the Spring context is fully initialized.
     *
     * @return the singleton instance of CommonProperties
     * @throws IllegalStateException if CommonProperties has not been initialized
     */
    public static CommonProperties getInstance() {
        var instance = INSTANCE.get();

        if (instance == null) {
            throw new IllegalStateException("CommonProperties has not been initialized");
        }

        return instance;
    }

    @Data
    @Validated
    public static class DatabaseStartupProperties {

        /**
         * Enables blocking application startup until the database is reachable.
         */
        private boolean enabled = true;

        /**
         * Driver connect timeout in seconds, passed as a JDBC property when supported.
         */
        @DurationMin(seconds = 1)
        @DurationUnit(ChronoUnit.SECONDS)
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(2);

        /**
         * Delay between retry attempts.
         */
        @DurationMin(millis = 500)
        @DurationUnit(ChronoUnit.SECONDS)
        @NotNull
        private Duration interval = Duration.ofSeconds(2);

        /**
         * Maximum total time to wait for the database.
         */
        @DurationMin(seconds = 10)
        @NotNull
        private Duration timeout = Duration.ofMinutes(5);

        /**
         * Driver socket timeout in seconds, passed as a JDBC property when supported.
         */
        @DurationMin(seconds = 1)
        @DurationUnit(ChronoUnit.SECONDS)
        @NotNull
        private Duration socketTimeout = Duration.ofSeconds(2);

        /**
         * JDBC validation timeout in seconds for Connection.isValid().
         */
        @DurationMax(seconds = Integer.MAX_VALUE)
        @DurationMin(seconds = 1)
        @DurationUnit(ChronoUnit.SECONDS)
        @NotNull
        private Duration validationTimeout = Duration.ofSeconds(2);
    }
}
