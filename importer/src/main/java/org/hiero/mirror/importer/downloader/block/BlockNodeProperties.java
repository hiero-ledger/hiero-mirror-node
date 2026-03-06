// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Comparator;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for a block node. Supports both static configuration (single host for status and streaming) and
 * configuration through discovered endpoints where status and subscribe_stream may have different hosts.
 */
@Data
@Validated
public class BlockNodeProperties implements Comparable<BlockNodeProperties> {

    private static final Comparator<BlockNodeProperties> COMPARATOR = Comparator.comparing(
                    BlockNodeProperties::getPriority)
            .thenComparing(BlockNodeProperties::getEffectiveStatusHost)
            .thenComparing(BlockNodeProperties::getEffectiveStreamingHost)
            .thenComparing(BlockNodeProperties::getStatusPort)
            .thenComparing(BlockNodeProperties::getStreamingPort);

    private String host;

    @Min(0)
    private int priority = 0;

    private String statusHost;

    @Max(65535)
    @Min(0)
    private int statusPort = 40840;

    private String streamingHost;

    @Max(65535)
    @Min(0)
    private int streamingPort = 40840;

    public String getEffectiveStatusHost() {
        return StringUtils.isNotBlank(statusHost) ? statusHost : host;
    }

    public String getEffectiveStreamingHost() {
        return StringUtils.isNotBlank(streamingHost) ? streamingHost : host;
    }

    @Override
    public int compareTo(BlockNodeProperties other) {
        return COMPARATOR.compare(this, other);
    }

    public String getStatusEndpoint() {
        return getEffectiveStatusHost() + ":" + statusPort;
    }

    public String getStreamingEndpoint() {
        return getEffectiveStreamingHost() + ":" + streamingPort;
    }
}
