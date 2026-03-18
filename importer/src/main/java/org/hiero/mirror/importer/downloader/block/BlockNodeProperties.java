// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Comparator;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class BlockNodeProperties implements Comparable<BlockNodeProperties> {

    private static final Comparator<BlockNodeProperties> COMPARATOR = Comparator.comparing(
                    BlockNodeProperties::getPriority)
            .thenComparing(BlockNodeProperties::getHost)
            .thenComparing(BlockNodeProperties::getStreamingHost)
            .thenComparing(BlockNodeProperties::getStatusPort)
            .thenComparing(BlockNodeProperties::getStreamingPort)
            .thenComparing(BlockNodeProperties::isStatusApiRequireTls)
            .thenComparing(BlockNodeProperties::isStreamingApiRequireTls);

    /**
     * Used for status and streaming (when streamingHost is not set)
     */
    @NotBlank
    private String host;

    @Min(0)
    private int priority = 0;

    private boolean statusApiRequireTls;

    private boolean streamingApiRequireTls;

    @Max(65535)
    @Min(0)
    private int statusPort = 40840;

    private String streamingHost;

    @Max(65535)
    @Min(0)
    private int streamingPort = 40840;

    @Override
    public int compareTo(BlockNodeProperties other) {
        return COMPARATOR.compare(this, other);
    }

    public String getStreamingHost() {
        return StringUtils.isNotBlank(streamingHost) ? streamingHost : host;
    }

    public String getStatusEndpoint() {
        return host + ":" + statusPort;
    }

    public String getStreamingEndpoint() {
        return getStreamingHost() + ":" + streamingPort;
    }

    /**
     * Returns a key that uniquely identifies this block node configuration for merge purposes.
     * Two configurations are considered the same when both status endpoint (host+port) and
     * requiresTls match, and both streaming endpoint (host+port) and requiresTls match.
     */
    public String getMergeKey() {
        return getStatusEndpoint()
                + "|" + statusApiRequireTls
                + "|" + getStreamingEndpoint()
                + "|" + streamingApiRequireTls;
    }
}
