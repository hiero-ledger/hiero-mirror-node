// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.grpc.ManagedChannelBuilder;
import jakarta.inject.Named;

@Named
final class ManagedChannelBuilderProviderImpl implements ManagedChannelBuilderProvider {

    @Override
    public ManagedChannelBuilder<?> get(BlockNodeProperties blockNodeProperties) {
        var builder = ManagedChannelBuilder.forTarget(blockNodeProperties.getEndpoint());
        if (blockNodeProperties.getStatusPort() != 443) {
            builder.usePlaintext();
        } else {
            builder.useTransportSecurity();
        }

        return builder;
    }
}
