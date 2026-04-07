// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.Collection;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.restjava.dto.RegisteredNodesRequest;

public interface RegisteredNodeService {
    Collection<RegisteredNode> getRegisteredNodes(RegisteredNodesRequest request);
}
