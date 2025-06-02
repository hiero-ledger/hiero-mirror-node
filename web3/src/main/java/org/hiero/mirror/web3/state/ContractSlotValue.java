// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import java.util.Arrays;

public record ContractSlotValue(byte[] slot, byte[] value) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ContractSlotValue that = (ContractSlotValue) o;

        if (!Arrays.equals(slot, that.slot)) {
            return false;
        }
        return Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(slot);
        result = 31 * result + Arrays.hashCode(value);
        return result;
    }
}
