// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.test.e2e.acceptance.props;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@Validated
public class NodeProperties {

    @NotBlank
    private String accountId;

    @NotBlank
    private String host;

    @Min(0)
    private int port = 50211;

    public String getEndpoint() {
        return host + ":" + port;
    }
}
