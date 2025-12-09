// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static com.hedera.services.utils.MiscUtils.perm64;

import java.util.Comparator;

/**
 * Copied Id type from hedera-services.
 *
 * Represents the id of a Hedera entity (account, topic, token, contract, file, or schedule).
 */
public record Id(long shard, long realm, long num) {
    public static final Id DEFAULT = new Id(0, 0, 0);
    public static final Comparator<Id> ID_COMPARATOR =
            Comparator.comparingLong(Id::num).thenComparingLong(Id::shard).thenComparingLong(Id::realm);

    @Override
    public int hashCode() {
        return (int) perm64(perm64(perm64(shard) ^ realm) ^ num);
    }

    @Override
    public String toString() {
        return String.format("%d.%d.%d", shard, realm, num);
    }
}
