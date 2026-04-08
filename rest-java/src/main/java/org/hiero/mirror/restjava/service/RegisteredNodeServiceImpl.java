// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.inject.Named;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.hiero.mirror.restjava.dto.RegisteredNodesRequest;
import org.hiero.mirror.restjava.repository.RegisteredNodeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Named
@RequiredArgsConstructor
final class RegisteredNodeServiceImpl implements RegisteredNodeService {

    private static final String REGISTERED_NODE_ID = "registered_node_id";
    private final RegisteredNodeRepository registeredNodeRepository;

    @Override
    public Collection<RegisteredNode> getRegisteredNodes(RegisteredNodesRequest request) {
        final var sort = Sort.by(request.getOrder(), REGISTERED_NODE_ID);
        final var page = PageRequest.of(0, request.getLimit(), sort);

        final var nodeType = request.getType();
        final long lowerBound = request.getLowerBound();
        final long upperBound = request.getUpperBound();

        if (nodeType == null) {
            return registeredNodeRepository.findByRegisteredNodeIdBetweenAndDeletedIsFalse(
                    lowerBound, upperBound, page);
        } else {
            return registeredNodeRepository.findByRegisteredNodeIdBetweenAndDeletedIsFalseAndTypeIn(
                    lowerBound, upperBound, nodeType.getId(), page);
        }
    }
}
