// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.grpc.ManagedChannelBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
final class ManagedChannelBuilderProviderImpl implements ManagedChannelBuilderProvider {

    private static final String TAG_SERVER = "server";

    private final MeterRegistry meterRegistry;

    @Override
    public ManagedChannelBuilder<?> get(BlockNodeProperties blockNodeProperties) {
        var endpoint = blockNodeProperties.getEndpoint();
        var interceptor = new MetricCollectingClientInterceptor(
                meterRegistry, counter -> counter.tag(TAG_SERVER, endpoint), timer -> timer.tag(TAG_SERVER, endpoint));
        var builder = ManagedChannelBuilder.forTarget(endpoint).intercept(interceptor);
        if (blockNodeProperties.getPort() != 443) {
            builder.usePlaintext();
        } else {
            builder.useTransportSecurity();
        }

        return builder;
    }
}
