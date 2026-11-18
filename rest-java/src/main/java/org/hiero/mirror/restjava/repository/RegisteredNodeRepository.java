// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.common.domain.node.RegisteredNode;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RegisteredNodeRepository
        extends PagingAndSortingRepository<RegisteredNode, Long>, RegisteredNodeRepositoryCustom {}
