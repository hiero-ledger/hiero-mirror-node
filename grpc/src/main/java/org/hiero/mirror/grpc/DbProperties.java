// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.grpc.db")
public class DbProperties {
    @NotBlank
    private String host = "";

    @NotBlank
    private String name = "";

    @NotBlank
    private String password = "";

    @Min(0)
    private int port = 5432;

    @Min(1000L)
    private long statementTimeout = 10_000L;

    @NotBlank
    private String username = "";
}
