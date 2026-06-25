// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.repository.projections.ContractBytecodeSnapshot;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractRepositoryTest extends Web3IntegrationTest {

    private final ContractRepository contractRepository;

    @Test
    void findRuntimeBytecodeSuccessfulCall() {
        Contract contract1 = domainBuilder.contract().persist();
        Contract contract2 = domainBuilder.contract().persist();
        assertThat(contractRepository.findRuntimeBytecode(contract1.getId()))
                .get()
                .isEqualTo(contract1.getRuntimeBytecode());

        contractRepository.deleteAll();

        assertThat(contractRepository.findRuntimeBytecode(contract1.getId()))
                .get()
                .isEqualTo(contract1.getRuntimeBytecode());
        assertThat(contractRepository.findRuntimeBytecode(contract2.getId())).isEmpty();
    }

    @Test
    void findRuntimeBytecodeFailCall() {
        Contract contract = domainBuilder.contract().persist();
        long id = contract.getId();
        assertThat(contractRepository.findRuntimeBytecode(++id)).isEmpty();
    }

    @Test
    void findByConsensusTimestamp() {
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.type(org.hiero.mirror.common.domain.entity.EntityType.CONTRACT))
                .persist();
        final var contract =
                domainBuilder.contract().customize(c -> c.id(entity.getId())).persist();
        domainBuilder.contract().persist();

        assertThat(contractRepository.findByConsensusTimestamp(entity.getTimestampLower()))
                .hasSize(1)
                .first()
                .returns(contract.getId(), Contract::getId)
                .returns(contract.getRuntimeBytecode(), Contract::getRuntimeBytecode);

        assertThat(contractRepository.findByConsensusTimestamp(entity.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findRuntimeBytecodesByIdsAndTimestamp() {
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.type(org.hiero.mirror.common.domain.entity.EntityType.CONTRACT))
                .persist();
        final var contract =
                domainBuilder.contract().customize(c -> c.id(entity.getId())).persist();
        final var timestamp = entity.getTimestampLower();

        assertThat(contractRepository.findRuntimeBytecodesByIds(List.of(contract.getId()), timestamp))
                .hasSize(1)
                .first()
                .returns(contract.getId(), ContractBytecodeSnapshot::getId)
                .returns(contract.getRuntimeBytecode(), ContractBytecodeSnapshot::getRuntimeBytecode);

        assertThat(contractRepository.findRuntimeBytecodesByIds(List.of(contract.getId()), timestamp - 1))
                .isEmpty();
    }
}
