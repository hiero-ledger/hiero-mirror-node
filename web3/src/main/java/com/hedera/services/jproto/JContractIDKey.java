// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.jproto;

import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Objects;

/**
 * Maps to proto Key of type contractID.
 *
 * <p>Just as a public-private key pair is used to control permissions for a Hedera entity (e.g., an
 * account), a {@code contractID} key is <i>also</i> used to control permissions for an entity. The
 * difference is that a {@code contractID} key requires a particular contract to be executing in
 * order to count as having "signed".
 *
 * <p>For example, suppose {@code 0.0.X} is an account. Its key is {@code Key{contractID=0.0.C}}.
 *
 * <p>Then <b>if</b> we are executing a contract operation, and the EVM receiver address in the
 * current frame is {@code 0.0.C}, then this EVM transaction can use system contracts to, for
 * example, transfer all the hbar from account {@code 0.0.X}.
 */
public class JContractIDKey extends JKey {
    private final long shardNum;
    private final long realmNum;
    private final long contractNum;

    public JContractIDKey(final ContractID contractID) {
        this.shardNum = contractID.getShardNum();
        this.realmNum = contractID.getRealmNum();
        this.contractNum = contractID.getContractNum();
    }

    @Override
    public JContractIDKey getContractIDKey() {
        return this;
    }

    @Override
    public boolean hasContractID() {
        return true;
    }

    public ContractID getContractID() {
        return ContractID.newBuilder()
                .setShardNum(shardNum)
                .setRealmNum(realmNum)
                .setContractNum(contractNum)
                .build();
    }

    public long getShardNum() {
        return shardNum;
    }

    public long getRealmNum() {
        return realmNum;
    }

    public long getContractNum() {
        return contractNum;
    }

    @Override
    public String toString() {
        return "<JContractID: " + shardNum + "." + realmNum + "." + contractNum + ">";
    }

    @Override
    public boolean isEmpty() {
        return (0 == contractNum);
    }

    @Override
    public boolean isValid() {
        return !isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        JContractIDKey that = (JContractIDKey) o;
        return this.shardNum == that.shardNum && this.realmNum == that.realmNum && this.contractNum == that.contractNum;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), shardNum, realmNum, contractNum);
    }
}
