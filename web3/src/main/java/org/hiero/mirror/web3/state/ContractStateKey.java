// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import java.util.Arrays;

public record ContractStateKey(Long contractId, byte[] slot) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ContractStateKey that = (ContractStateKey) o;

        if (!contractId.equals(that.contractId)) {
            return false;
        }
        return Arrays.equals(slot, that.slot);
    }

    @Override
    public int hashCode() {
        int result = contractId.hashCode();
        result = 31 * result + Arrays.hashCode(slot);
        return result;
    }
}
