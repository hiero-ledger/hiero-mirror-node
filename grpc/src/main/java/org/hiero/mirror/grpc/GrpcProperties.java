// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hiero.mirror.grpc.config.NettyProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.grpc")
public class GrpcProperties {

    private boolean checkTopicExists = true;

    @NotNull
    private Duration endTimeInterval = Duration.ofSeconds(30);

    @Min(1)
    private int entityCacheSize = 50_000;

    @NotNull
    @Valid
    private NettyProperties netty = new NettyProperties();
}
