// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.contractlog;

import java.util.Collection;
import java.util.Map;

/**
 * A batch lookup for entities' EVM address aliases, backed by a shared cache. Lets components that need to resolve EVM
 * addresses (e.g. receipts root calculation) reuse the same cache the synthetic log processing relies on, resolving all
 * ids in one round trip instead of maintaining their own batched resolution.
 */
public interface EvmAddressCache {

    /**
     * @param entityIds the entity ids to resolve
     * @return the trimmed EVM address aliases by entity id; ids without an alias are absent from the map
     */
    Map<Long, byte[]> getAll(Collection<Long> entityIds);
}
