// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store.contract;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;

import com.hedera.mirror.common.CommonProperties;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;

@Named
@RequiredArgsConstructor
public class EntityAddressSequencer {
    private final AtomicLong entityIds = new AtomicLong(1_000_000_000);
    private final CommonProperties commonProperties;

    public ContractID getNewContractId(Address sponsor) {
        final var newContractSponsor = accountIdFromEvmAddress(sponsor.toArrayUnsafe());
        return ContractID.newBuilder()
                .setRealmNum(newContractSponsor.getRealmNum())
                .setShardNum(newContractSponsor.getShardNum())
                .setContractNum(getNextEntityId())
                .build();
    }

    public AccountID getNewAccountId() {
        return AccountID.newBuilder()
                .setRealmNum(commonProperties.getRealm())
                .setShardNum(commonProperties.getShard())
                .setAccountNum(getNextEntityId())
                .build();
    }

    private long getNextEntityId() {
        return entityIds.getAndIncrement();
    }
}
