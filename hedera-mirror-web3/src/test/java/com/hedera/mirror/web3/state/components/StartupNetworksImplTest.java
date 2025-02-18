/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.state.components;

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
