// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import java.util.Arrays;

/**
 * Historical cache key for a contract storage slot at a block timestamp. Extends {@link ContractStateCacheKey} with the
 * timestamp so the same slot at different blocks does not collide.
 */
final class ContractStateHistoricalCacheKey extends ContractStateCacheKey {

    private final long timestamp;
    // Memoized for performance reasons
    private final int hash;

    private ContractStateHistoricalCacheKey(final long contractId, final byte[] slot, final long timestamp) {
        super(contractId, slot);
        this.timestamp = timestamp;
        this.hash = 31 * super.hash() + Long.hashCode(timestamp);
    }

    static ContractStateHistoricalCacheKey of(final long contractId, final byte[] slot, final long timestamp) {
        return new ContractStateHistoricalCacheKey(contractId, slot, timestamp);
    }

    long timestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (ContractStateHistoricalCacheKey) o;
        return hash == that.hash
                && timestamp == that.timestamp
                && contractId() == that.contractId()
                && Arrays.equals(slot(), that.slot());
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ContractStateHistoricalCacheKey(contractId="
                + contractId()
                + ", slot="
                + Arrays.toString(slot())
                + ", timestamp="
                + timestamp
                + ")";
    }
}
