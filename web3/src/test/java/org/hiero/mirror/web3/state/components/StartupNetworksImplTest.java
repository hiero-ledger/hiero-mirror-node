// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.internal.network.Network;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartupNetworksImplTest {

    private StartupNetworksImpl startupNetworks;

    @BeforeEach
    void setUp() {
        startupNetworks = new StartupNetworksImpl();
    }

    @Test
    void testGenesisNetworkOrThrow() {
        assertThat(startupNetworks.genesisNetworkOrThrow(mock(Configuration.class)))
                .isEqualTo(Network.DEFAULT);
    }

    @Test
    void testOverrideNetworkFor() {
        final Configuration configuration = new ConfigProviderImpl().getConfiguration();
        assertThat(startupNetworks.overrideNetworkFor(0, configuration)).isEmpty();
    }

    @Test
    void testSetOverrideRound() {
        assertDoesNotThrow(() -> startupNetworks.setOverrideRound(0));
    }

    @Test
    void testArchiveStartupNetworks() {
        assertDoesNotThrow(() -> startupNetworks.archiveStartupNetworks());
    }
}
