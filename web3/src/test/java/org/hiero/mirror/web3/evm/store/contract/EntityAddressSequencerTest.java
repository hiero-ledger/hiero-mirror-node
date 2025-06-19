// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.contract;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityAddressSequencerTest {
    private static final DomainBuilder domainBuilder = new DomainBuilder();
    private static final long CONTRACT_NUM = 1_000_000_000L;
    private static final EntityId sponsorEntity = domainBuilder.entityId();
    private static final Address sponsor =
            EntityIdUtils.idFromEntityId(sponsorEntity).asEvmAddress();

    @Mock
    private CommonProperties commonProperties;

    @InjectMocks
    private EntityAddressSequencer entityAddressSequencer;

    @Test
    void getNewContractId() {
        assertThat(entityAddressSequencer.getNewContractId(sponsor))
                .returns(sponsorEntity.getShard(), ContractID::getShardNum)
                .returns(sponsorEntity.getRealm(), ContractID::getRealmNum)
                .returns(CONTRACT_NUM, ContractID::getContractNum);
    }

    @Test
    void getNewAccountId() {
        assertThat(entityAddressSequencer.getNewAccountId())
                .returns(0L, AccountID::getRealmNum)
                .returns(0L, AccountID::getShardNum)
                .returns(1000000000L, AccountID::getAccountNum);
        assertThat(entityAddressSequencer.getNewAccountId())
                .returns(0L, AccountID::getRealmNum)
                .returns(0L, AccountID::getShardNum)
                .returns(1000000001L, AccountID::getAccountNum);
    }

    @Test
    void getNextEntityIdReturnsNextId() {
        final var actualAddress = entityAddressSequencer.getNewContractId(sponsor);
        assertThat(actualAddress.getContractNum()).isEqualTo(CONTRACT_NUM);
    }

    @Test
    void getNextEntityIdWorksCorrectlyAfterMultipleCalls() {
        entityAddressSequencer.getNewContractId(sponsor);
        entityAddressSequencer.getNewContractId(sponsor);
        final var actual = entityAddressSequencer.getNewContractId(sponsor);

        assertThat(actual.getContractNum()).isEqualTo(CONTRACT_NUM + 2);
    }
}
