// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import com.hedera.node.internal.network.Network;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.StartupNetworks;
import jakarta.inject.Named;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

@Named
public class StartupNetworksImpl implements StartupNetworks {

    @Override
    public Network genesisNetworkOrThrow(@NonNull Configuration platformConfig) {
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
