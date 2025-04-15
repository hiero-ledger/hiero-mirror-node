// SPDX-License-Identifier: Apache-2.0

package com.hedera.node.app.service.evm.accounts;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.util.DomainUtils;
import java.util.Arrays;
import org.hyperledger.besu.datatypes.Address;

public abstract class HederaEvmContractAliases {

    public static final int EVM_ADDRESS_LEN = 20;

    public abstract Address resolveForEvm(Address addressOrAlias);

    public boolean isMirror(final Address address) {
        return isMirror(address.toArrayUnsafe());
    }

    public static boolean isMirror(final byte[] address) {
        if (address.length != EVM_ADDRESS_LEN) {
            return false;
        }

        if (Arrays.equals(address, Address.ZERO.toArray())) {
            return true;
        }

        var entityId = DomainUtils.fromEvmAddress(address);
        var commonProperties = CommonProperties.getInstance();
        return entityId != null
                && entityId.getShard() == commonProperties.getShard()
                && entityId.getRealm() == commonProperties.getRealm();
    }
}
