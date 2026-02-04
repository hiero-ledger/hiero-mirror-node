// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class NettyProperties {

    @Min(1)
    private int maxConcurrentCallsPerConnection = 5;
}
