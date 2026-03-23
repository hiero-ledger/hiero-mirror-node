// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.grpc.ManagedChannelBuilder;
import jakarta.inject.Named;

@Named
final class ManagedChannelBuilderProviderImpl implements ManagedChannelBuilderProvider {

    @Override
    public ManagedChannelBuilder<?> get(final String host, final int port, final boolean useTls) {
        final var builder = ManagedChannelBuilder.forAddress(host, port);
        if (useTls) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }

        return builder;
    }
}
