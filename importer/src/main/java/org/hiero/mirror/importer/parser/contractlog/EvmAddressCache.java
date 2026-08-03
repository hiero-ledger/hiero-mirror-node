// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.jspecify.annotations.Nullable;

/**
 * A lookup for an entity's EVM address alias, backed by a shared cache. Lets components that need to resolve EVM
 * addresses (e.g. receipts root calculation) reuse the same cache the synthetic log processing relies on, instead of
 * maintaining their own resolution.
 */
public interface EvmAddressCache {

    /**
     * @param entityId the entity to resolve
     * @return the entity's trimmed EVM address alias, or {@code null} if it has none
     */
    @Nullable
    byte[] get(EntityId entityId);

    /**
     * Caches the entity's trimmed EVM address alias.
     *
     * @param entityId   the entity
     * @param evmAddress the entity's trimmed EVM address alias
     */
    void put(EntityId entityId, byte[] evmAddress);
}
