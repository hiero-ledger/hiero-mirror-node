// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import java.util.List;
import java.util.TreeSet;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSortedSet;

public final class BlockNodeTestUtils {

    public static BlockNodeProperties.ServiceEndpoint fullServiceEndpoint(final String host, final int port) {
        final var endpoint = new BlockNodeProperties.ServiceEndpoint();
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
    }

    public static BlockNodeProperties.ServiceEndpoint singleServiceEndpoint(
            final BlockNodeApi api, final String host, final int port) {
        final var endpoint = new BlockNodeProperties.ServiceEndpoint();
        endpoint.setApis(ImmutableSortedSet.of(api));
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
    }

    public static BlockNodeProperties singleEndpointProperties(final String host) {
        return singleEndpointProperties(host, 40840);
    }

    public static BlockNodeProperties singleEndpointProperties(final String host, final int port) {
        final var endpoint = fullServiceEndpoint(host, port);
        final var properties = new BlockNodeProperties();
        properties.setEndpoints(new TreeSet<>(List.of(endpoint)));
        return properties;
    }
}
