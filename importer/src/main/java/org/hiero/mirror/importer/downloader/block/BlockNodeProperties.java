// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class BlockNodeProperties {

    @NotEmpty
    private String host;

    @Max(65535)
    @Min(1)
    private int port;

    @Min(0)
    private int priority = 0;

    public boolean isInProcess() {
        return host.startsWith("in-process:");
    }

    public String getEndpoint() {
        if (isInProcess()) {
            return host.replace("in-process:", "");
        }

        return host + ":" + port;
    }
}
