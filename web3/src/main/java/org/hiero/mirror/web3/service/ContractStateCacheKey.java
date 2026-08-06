// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import java.util.Arrays;

/**
 * Cache key for a single contract storage slot value. Slot keys use deep equality so distinct {@code byte[]} instances
 * with the same contents collide correctly. The hash is memoized because a key is rebuilt for every storage read.
 */
class ContractStateCacheKey {

    private final long contractId;
    private final byte[] slot;
    // Memoized for performance reasons
    private final int hash;

    protected ContractStateCacheKey(final long contractId, final byte[] slot) {
        this.contractId = contractId;
        this.slot = slot;
        this.hash = 31 * Long.hashCode(contractId) + Arrays.hashCode(slot);
    }

    static ContractStateCacheKey of(final long contractId, final byte[] slot) {
        return new ContractStateCacheKey(contractId, slot);
    }

    long contractId() {
        return contractId;
    }

    byte[] slot() {
        return slot;
    }

    protected int hash() {
        return hash;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (ContractStateCacheKey) o;
        return hash == that.hash && contractId == that.contractId && Arrays.equals(slot, that.slot);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ContractStateCacheKey(contractId=" + contractId + ", slot=" + Arrays.toString(slot) + ")";
    }
}
