// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance.line;

import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.exception.InvalidDatasetException;

public interface AccountBalanceLineParser {
    String INVALID_BALANCE = "Invalid account balance line: ";

    AccountBalance parse(String line, long consensusTimestamp);

    /**
     * Converts shard, realm, num to an {@link EntityId}, rejecting values outside the supported ranges regardless of
     * how {@link EntityId} is configured to handle invalid input. When invalid entity exceptions are disabled (as the
     * importer does), {@link EntityId#of(long, long, long)} silently returns {@link EntityId#EMPTY} instead of
     * throwing, which would otherwise turn an out-of-range line into a bogus 0.0.0 record.
     *
     * @throws InvalidDatasetException if the entity id is out of range
     */
    static EntityId toEntityId(long shard, long realm, long num) {
        final var entityId = EntityId.of(shard, realm, num);
        if (EntityId.EMPTY.equals(entityId) && (shard != 0 || realm != 0 || num != 0)) {
            throw new InvalidDatasetException(String.format("Entity ID out of range: %d.%d.%d", shard, realm, num));
        }
        return entityId;
    }
}
