// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import com.hedera.mirror.common.domain.node.Node;
import org.springframework.data.repository.CrudRepository;

public interface NodeRepository extends CrudRepository<Node, Long> {}
