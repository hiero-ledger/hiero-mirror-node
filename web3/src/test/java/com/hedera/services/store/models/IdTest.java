// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.models;

import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hederahashgraph.api.proto.java.ContractID;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.util.DomainUtils;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class IdTest {
    @Test
    void hashCodeDiscriminates() {
        final var aId = new Id(1, 2, 3);
        final var bId = new Id(0, 2, 3);
        final var cId = new Id(1, 0, 3);
        final var dId = new Id(1, 2, 0);
        final var eId = new Id(1, 2, 3);

        assertNotEquals(bId.hashCode(), aId.hashCode());
        assertNotEquals(cId.hashCode(), aId.hashCode());
        assertNotEquals(dId.hashCode(), aId.hashCode());
        assertEquals(eId.hashCode(), aId.hashCode());
    }

    @Test
    void equalsDiscriminates() {
        final var aId = new Id(1, 2, 3);
        final var bId = new Id(0, 2, 3);
        final var cId = new Id(1, 0, 3);
        final var dId = new Id(1, 2, 0);
        final var eId = new Id(1, 2, 3);

        assertNotEquals(bId, aId);
        assertNotEquals(cId, aId);
        assertNotEquals(dId, aId);
        assertEquals(eId, aId);
        assertNotEquals(aId, new Object());
        assertEquals(aId, aId);
    }

    @Test
    void conversionsWork() {
        final var id = new Id(1, 2, 3);
        final var grpcAccount = asAccount("1.2.3");
        final var grpcToken = asToken("1.2.3");
        final var contractId = ContractID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setContractNum(3)
                .build();
        final var address = Address.wrap(Bytes.wrap(DomainUtils.toEvmAddress(contractId)));

        assertEquals(grpcAccount, id.asGrpcAccount());
        assertEquals(grpcToken, id.asGrpcToken());
        assertEquals(contractId, id.asGrpcContract());
        assertEquals(address, id.asEvmAddress());
        assertEquals(id, Id.fromGrpcAccount(grpcAccount));
        assertEquals(id, Id.fromGrpcToken(grpcToken));
        assertEquals(id, Id.fromGrpcContract(contractId));
    }

    @Test
    void gettersWork() {
        final var id = new Id(11, 22, 33);

        assertEquals(11, id.shard());
        assertEquals(22, id.realm());
        assertEquals(33, id.num());
    }

    @Test
    void toStringWorks() {
        final var id = new Id(4, 5, 6);

        assertEquals("4.5.6", id.toString());
    }

    @Test
    void comparatorWorks() {
        final var a = new Id(0, 0, 1);
        final var b = new Id(1, 0, 0);
        final var c = new Id(0, 1, 0);
        final var l = new ArrayList<Id>();

        l.add(a);
        l.add(b);
        l.add(c);
        l.sort(Id.ID_COMPARATOR);

        assertEquals(List.of(c, b, a), l);
    }
}
