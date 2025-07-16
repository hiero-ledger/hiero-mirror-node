// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.importer.db")
public class DBProperties {
    private String connectionInitSql = "";

    @NotBlank
    private String host = "";

    private boolean loadBalance = true;

    @NotBlank
    private String name = "";

    @NotBlank
    private String owner = "";

    @NotBlank
    private String ownerPassword = "";

    @NotBlank
    private String password = "";

    @Min(0)
    private int port = 5432;

    @NotBlank
    private String restPassword = "";

    @NotBlank
    private String restUsername = "";

    @NotBlank
    private String schema = "";

    @NotBlank
    private String tempSchema = "";

    @NotBlank
    private String username = "";

    @NotBlank
    private String sslMode = "";

    @NotNull
    private Duration metricRefreshInterval = Duration.ofMinutes(5L);
}
