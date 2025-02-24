// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.jproto;

import com.hederahashgraph.api.proto.java.ContractID;

/** Maps to proto Key of type contractID. */
public class JDelegatableContractIDKey extends JContractIDKey {
    public JDelegatableContractIDKey(final ContractID contractID) {
        super(contractID);
    }

    @Override
    public JDelegatableContractIDKey getDelegatableContractIdKey() {
        return this;
    }

    @Override
    public boolean hasDelegatableContractId() {
        return true;
    }

    @Override
    public boolean hasContractID() {
        return false;
    }

    @Override
    public JContractIDKey getContractIDKey() {
        return null;
    }

    @Override
    public String toString() {
        return "<JDelegatableContractId: " + getShardNum() + "." + getRealmNum() + "." + getContractID() + ">";
    }
}
