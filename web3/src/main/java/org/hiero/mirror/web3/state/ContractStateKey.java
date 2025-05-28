// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;

public class ContractStateKey {
    private final Long contractId;
    private final Bytes32 slot;

    public ContractStateKey(Long contractId, byte[] slotBytes) {
        if (slotBytes.length != 32) {
            throw new IllegalArgumentException("Slot must be exactly 32 bytes");
        }
        this.contractId = contractId;
        this.slot = Bytes32.wrap(slotBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContractStateKey other)) return false;
        return Objects.equals(contractId, other.contractId) && Objects.equals(slot, other.slot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contractId, slot);
    }

    @Override
    public String toString() {
        return "ContractStateKey{" + "contractId=" + contractId + ", slot=" + slot + '}';
    }
}
