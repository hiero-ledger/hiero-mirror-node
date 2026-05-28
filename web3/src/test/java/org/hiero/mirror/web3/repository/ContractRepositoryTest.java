// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.Contract;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractRepositoryTest extends Web3IntegrationTest {

    private final ContractRepository contractRepository;

    @Test
    void findRuntimeBytecodeSuccessfulCall() {
        Contract contract1 = domainBuilder.contract().persist();
        Contract contract2 = domainBuilder.contract().persist();
        assertThat(contractRepository.findById(contract1.getId()))
                .map(Contract::getRuntimeBytecode)
                .get()
                .isEqualTo(contract1.getRuntimeBytecode());

        contractRepository.deleteById(contract2.getId());

        assertThat(contractRepository.findById(contract1.getId()))
                .map(Contract::getRuntimeBytecode)
                .get()
                .isEqualTo(contract1.getRuntimeBytecode());
        assertThat(contractRepository.findById(contract2.getId())).isEmpty();
    }

    @Test
    void findRuntimeBytecodeFailCall() {
        Contract contract = domainBuilder.contract().persist();
        long id = contract.getId();
        assertThat(contractRepository.findById(++id)).isEmpty();
    }
}
