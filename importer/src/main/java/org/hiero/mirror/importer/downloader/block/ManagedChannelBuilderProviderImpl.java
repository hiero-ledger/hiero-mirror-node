// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.grpc.ManagedChannelBuilder;
import jakarta.inject.Named;

@Named
final class ManagedChannelBuilderProviderImpl implements ManagedChannelBuilderProvider {

    @Override
    public ManagedChannelBuilder<?> get(BlockNodeProperties blockNodeProperties) {
        return createBuilder(blockNodeProperties.getStatusEndpoint(), blockNodeProperties.getStatusPort());
    }

    @Override
    public ManagedChannelBuilder<?> getForStreaming(BlockNodeProperties blockNodeProperties) {
        return createBuilder(blockNodeProperties.getStreamingEndpoint(), blockNodeProperties.getStreamingPort());
    }

    private ManagedChannelBuilder<?> createBuilder(String endpoint, int port) {
        var builder = ManagedChannelBuilder.forTarget(endpoint);
        if (port != 443) {
            builder.usePlaintext();
        } else {
            builder.useTransportSecurity();
        }

        return builder;
    }
}
