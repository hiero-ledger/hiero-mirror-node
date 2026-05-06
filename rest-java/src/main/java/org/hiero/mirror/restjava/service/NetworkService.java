// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.Collection;
import java.util.List;
import org.hiero.mirror.common.domain.addressbook.NetworkStake;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.hiero.mirror.restjava.dto.NetworkNodeRequest;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.dto.RegisteredNodesRequest;

public interface NetworkService {

    NetworkStake getLatestNetworkStake();

    List<NetworkNodeDto> getNetworkNodes(NetworkNodeRequest request);

    Collection<RegisteredNode> getRegisteredNodes(RegisteredNodesRequest request);

    NetworkSupply getSupply(Bound timestamp);
}
