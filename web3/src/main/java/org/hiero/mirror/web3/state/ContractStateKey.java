// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import org.apache.tuweni.bytes.Bytes32;

public record ContractStateKey(Long contractId, Bytes32 slot) {

    public ContractStateKey(Long contractId, byte[] slotBytes) {
        this(contractId, validateAndWrap(slotBytes));
    }

    private static Bytes32 validateAndWrap(byte[] slotBytes) {
        if (slotBytes == null || slotBytes.length != 32) {
            throw new IllegalArgumentException("Slot must be exactly 32 bytes");
        }
        return Bytes32.wrap(slotBytes);
    }
}
