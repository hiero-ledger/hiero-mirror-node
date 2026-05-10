// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannelBuilder;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
final class ManagedChannelBuilderProviderImpl implements ManagedChannelBuilderProvider {

    private final ZstdCodec zstdCodec;

    @Override
    public ManagedChannelBuilder<?> get(final String host, final int port, final boolean useTls) {
        final var builder = ManagedChannelBuilder.forAddress(host, port);
        if (useTls) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }

        builder.decompressorRegistry(DecompressorRegistry.getDefaultInstance().with(zstdCodec, true));
        return builder;
    }
}
