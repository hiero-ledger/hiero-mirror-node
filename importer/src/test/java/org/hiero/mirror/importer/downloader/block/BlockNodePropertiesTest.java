// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.fullServiceEndpoint;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.singleEndpointProperties;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.singleServiceEndpoint;

import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.junit.jupiter.api.Test;

final class BlockNodePropertiesTest {

    @Test
    void compare() {
        final var propertiesA = singleEndpointProperties("a");
        final var propertiesB = singleEndpointProperties("b");
        final var propertiesC = singleEndpointProperties("b", 60000);
        final var propertiesLowerPriority = singleEndpointProperties("a");
        propertiesLowerPriority.setPriority(1);
        assertThat(propertiesA.compareTo(propertiesA)).isZero();
        assertThat(propertiesA.compareTo(propertiesB)).isNegative();
        assertThat(propertiesB.compareTo(propertiesC)).isNegative();
        assertThat(propertiesA.compareTo(propertiesLowerPriority)).isNegative();
    }

    @Test
    void compareServiceEndpoint() {
        final var serviceEndpointA = singleServiceEndpoint(BlockNodeApi.STATUS, "a", 40840);
        final var serviceEndpointB = singleServiceEndpoint(BlockNodeApi.SUBSCRIBE_STREAM, "a", 40840);
        final var serviceEndpointC = fullServiceEndpoint("a", 40840);
        final var serviceEndpointD = fullServiceEndpoint("b", 40840);
        final var serviceEndpointE = fullServiceEndpoint("a", 40841);
        assertThat(serviceEndpointA.compareTo(serviceEndpointA)).isZero();
        assertThat(serviceEndpointA.compareTo(serviceEndpointB)).isNegative();
        assertThat(serviceEndpointA.compareTo(serviceEndpointC)).isNegative();
        assertThat(serviceEndpointC.compareTo(serviceEndpointD)).isNegative();
        assertThat(serviceEndpointC.compareTo(serviceEndpointE)).isNegative();
    }
}
