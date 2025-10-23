// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Named
@RequiredArgsConstructor
public class NetworkInfoImpl implements NetworkInfo {

    @NonNull
    @Override
    public Bytes ledgerId() {
        throw new UnsupportedOperationException("Ledger ID is not supported.");
    }

    @NonNull
    @Override
    public NodeInfo selfNodeInfo() {
        return nodeInfo();
    }

    @NonNull
    @Override
    public List<NodeInfo> addressBook() {
        return List.of(nodeInfo());
    }

    @Nullable
    @Override
    public NodeInfo nodeInfo(long nodeId) {
        return null;
    }

    @Override
    public boolean containsNode(final long nodeId) {
        return nodeInfo(nodeId) != null;
    }

    @Override
    public void updateFrom(State state) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Returns a {@link NodeInfo} that is a complete mock other than the software version present in the given
     * configuration.
     *
     * @return a mock self node info
     */
    private NodeInfo nodeInfo() {
        return new NodeInfo() {
            @Override
            public long nodeId() {
                return 0;
            }

            @Override
            public AccountID accountId() {
                return AccountID.DEFAULT;
            }

            @Override
            public long weight() {
                return 0;
            }

            @Override
            public Bytes sigCertBytes() {
                return Bytes.EMPTY;
            }

            @Override
            public List<ServiceEndpoint> gossipEndpoints() {
                return Collections.emptyList();
            }

            @NonNull
            @Override
            public List<ServiceEndpoint> hapiEndpoints() {
                return Collections.emptyList();
            }

            @Override
            public boolean declineReward() {
                return false;
            }

            @Override
            public Bytes grpcCertHash() {
                return Bytes.EMPTY;
            }
        };
    }
}
