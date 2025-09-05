// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.info.NodeInfo;
import jakarta.annotation.Resource;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

class NetworkInfoImplTest extends Web3IntegrationTest {

    private static final int NODE_ID = 2;

    @Resource
    private NetworkInfoImpl networkInfoImpl;

    @Test
    void testLedgerId() {
        final var exception = assertThrows(UnsupportedOperationException.class, () -> networkInfoImpl.ledgerId());
        assertThat(exception.getMessage()).isEqualTo("Ledger ID is not supported.");
    }

    @Test
    void testSelfNodeInfo() {
        NodeInfo selfNodeInfo = networkInfoImpl.selfNodeInfo();
        assertThat(selfNodeInfo).isNotNull().satisfies(info -> {
            assertThat(info.nodeId()).isZero();
            assertThat(info.accountId()).isEqualTo(AccountID.DEFAULT);
            assertThat(info.weight()).isZero();
            assertThat(info.sigCertBytes()).isEqualTo(Bytes.EMPTY);
            assertThat(info.gossipEndpoints()).isEmpty();
            assertThat(info.grpcCertHash()).isEqualTo(Bytes.EMPTY);
        });
    }

    @Test
    void testUpdateFrom() {
        final var exception = assertThrows(UnsupportedOperationException.class, () -> networkInfoImpl.updateFrom(null));
        assertThat(exception.getMessage()).isEqualTo("Not implemented");
    }

    @Test
    void testAddressBook() {
        assertThat(networkInfoImpl.addressBook()).isNotEmpty();
    }

    @Test
    void testNodeInfo() {
        assertThat(networkInfoImpl.nodeInfo(NODE_ID)).isNull();
    }

    @Test
    void testNodeInfoInvalid() {
        NodeInfo nodeInfo = networkInfoImpl.nodeInfo(Integer.MAX_VALUE);
        assertThat(nodeInfo).isNull();
    }

    @Test
    void testContainsNode() {
        assertThat(networkInfoImpl.containsNode(NODE_ID)).isFalse();
    }

    @Test
    void testContainsNodeInvalid() {
        assertThat(networkInfoImpl.containsNode(Integer.MAX_VALUE)).isFalse();
    }
}
