// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;

/**
 * Resolves a {@link EntityId} based on address in byte[].
 */
@FunctionalInterface
public interface ContractAccountResolver {

    Optional<EntityId> lookup(byte[] accountAddress);
}
