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

import com.hedera.node.internal.network.Network;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.StartupNetworks;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Optional;

@Named
public class StartupNetworksImpl implements StartupNetworks {

    @Override
    public Network genesisNetworkOrThrow(@Nonnull Configuration platformConfig) {
        return Network.DEFAULT;
    }

    @Override
    public Optional<Network> overrideNetworkFor(long roundNumber, Configuration platformConfig) {
        return Optional.empty();
    }

    @Override
    public void setOverrideRound(long roundNumber) {
        // This is a no-op in the current context, and other implementations may provide behavior.
    }

    @Override
    public void archiveStartupNetworks() {
        // This is a no-op in the current context, and other implementations may provide behavior.
    }

    /**
     * @deprecated in the StartupNetworks interface
     */
    @SuppressWarnings({"java:S1133", "java:S6355"})
    @Deprecated
    @Override
    public Network migrationNetworkOrThrow(Configuration platformConfig) {
        return Network.DEFAULT;
    }
}
