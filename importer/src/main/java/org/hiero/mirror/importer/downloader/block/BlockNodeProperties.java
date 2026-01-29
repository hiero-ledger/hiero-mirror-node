// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class BlockNodeProperties {

    @NotBlank
    private String host;

    @Min(0)
    private int priority = 0;

    @Max(65535)
    @Min(0)
    private int statusPort = 40840;

    @Max(65535)
    @Min(0)
    private int streamingPort = 40840;

    public String getStatusEndpoint() {
        return host + ":" + statusPort;
    }
}
