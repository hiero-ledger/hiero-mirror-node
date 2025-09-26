// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Comparator;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class BlockNodeProperties implements Comparable<BlockNodeProperties> {

    private static final Comparator<BlockNodeProperties> COMPARATOR = Comparator.comparing(
                    BlockNodeProperties::getPriority)
            .thenComparing(BlockNodeProperties::getHost)
            .thenComparing(BlockNodeProperties::getPort);

    @NotBlank
    private String host;

    @Max(65535)
    @Min(0)
    private int port = 40840;

    @Min(0)
    private int priority = 0;

    @Override
    public int compareTo(BlockNodeProperties other) {
        return COMPARATOR.compare(this, other);
    }

    public String getEndpoint() {
        return host + ":" + port;
    }
}
