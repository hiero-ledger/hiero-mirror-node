// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.subscribe.grpc;

import com.hedera.mirror.monitor.subscribe.AbstractSubscriberProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class GrpcSubscriberProperties extends AbstractSubscriberProperties {

    @NotNull
    private Instant startTime = Instant.now();

    @NotBlank
    private String topicId;

    public GrpcSubscriberProperties() {
        retry.setMaxAttempts(Long.MAX_VALUE); // gRPC subscription only occurs once so retry indefinitely
        retry.setMaxBackoff(Duration.ofSeconds(8L));
    }

    public Instant getEndTime() {
        return startTime.plus(duration);
    }
}
