// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import java.util.Collection;
import java.util.Set;
import lombok.Value;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;

/**
 * Collection of fields that can be used by Transaction Filter to filter on.
 */
@Value
@SuppressWarnings("java:S6548") // Class is not a singleton
public class TransactionFilterFields {
    public static final TransactionFilterFields EMPTY = new TransactionFilterFields(Set.of(), null);
    /**
     * entities contains: (1) Main entity associated with the transaction (2) Transaction payer account (when present)
     * (3) crypto transfer receivers/senders
     */
    Collection<EntityId> entities;

    RecordItem recordItem;
}
